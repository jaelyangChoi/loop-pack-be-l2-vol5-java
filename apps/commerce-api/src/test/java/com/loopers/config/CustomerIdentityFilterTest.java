package com.loopers.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.UserService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring 없이 필터만 검증한다. 사용자 저장소는 메모리 대역을 쓰고, 경로 목록은 실제 설정의 상수를 쓴다.
 */
class CustomerIdentityFilterTest {

    private static final long EXISTING_USER_ID = 1L;
    private static final String USER_ID_HEADER = "X-USER-ID";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CustomerIdentityFilter filter = new CustomerIdentityFilter(
        SecurityConfig.IDENTIFIED_PATHS,
        new UserService(new FakeUserRepository(Set.of(EXISTING_USER_ID))),
        new SecurityErrorResponseWriter(objectMapper)
    );

    // 다음 필터로 넘어갔는지와, 넘어간 시점의 인증 정보를 기록한다.
    private boolean passed;
    private Authentication passedAuthentication;
    private final FilterChain chain = (request, response) -> {
        passed = true;
        passedAuthentication = SecurityContextHolder.getContext().getAuthentication();
    };

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse doFilter(String method, String uri, String userIdHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        // 경로 매칭은 servletPath 를 본다. DispatcherServlet 이 "/" 에 매핑된 실제 요청처럼 전체 경로를 둔다.
        request.setServletPath(uri);
        if (userIdHeader != null) {
            request.addHeader(USER_ID_HEADER, userIdHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private void assertRejected(MockHttpServletResponse response, int status, String errorCode, String message) throws Exception {
        assertThat(passed).isFalse();
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode meta = objectMapper.readTree(response.getContentAsString()).get("meta");
        assertThat(meta.get("result").asText()).isEqualTo("FAIL");
        assertThat(meta.get("errorCode").asText()).isEqualTo(errorCode);
        if (message != null) {
            assertThat(meta.get("message").asText()).isEqualTo(message);
        }
    }

    @DisplayName("식별이 필요한 경로이면,")
    @Nested
    class IdentifiedPath {
        @DisplayName("X-USER-ID 헤더가 없으면 400 INVALID_INPUT 으로 거절하고 다음으로 넘기지 않는다.")
        @ParameterizedTest
        @CsvSource({
            "POST, /api/v1/points/charge",
            "GET, /api/v1/points",
            "POST, /api/v1/products/1/likes",
            "DELETE, /api/v1/products/1/likes",
            "GET, /api/v1/users/1/likes",
        })
        void rejectsWithInvalidInput_whenHeaderIsMissing(String method, String uri) throws Exception {
            MockHttpServletResponse response = doFilter(method, uri, null);

            assertRejected(response, 400, "INVALID_INPUT", "필수 요청 헤더 'X-USER-ID'가 누락되었습니다.");
        }

        @DisplayName("X-USER-ID 가 숫자가 아니면 400 INVALID_INPUT 으로 거절하고 다음으로 넘기지 않는다.")
        @ParameterizedTest
        @ValueSource(strings = {"abc", "", "1.5", "99999999999999999999"})
        void rejectsWithInvalidInput_whenHeaderIsNotNumber(String header) throws Exception {
            MockHttpServletResponse response = doFilter("GET", "/api/v1/points", header);

            assertRejected(response, 400, "INVALID_INPUT", null);
        }

        @DisplayName("존재하지 않는 사용자이면 404 NOT_FOUND 로 거절하고 다음으로 넘기지 않는다.")
        @Test
        void rejectsWithNotFound_whenUserDoesNotExist() throws Exception {
            MockHttpServletResponse response = doFilter("POST", "/api/v1/products/1/likes", "999");

            assertRejected(response, 404, "NOT_FOUND", "존재하지 않는 사용자입니다.");
        }

        @DisplayName("존재하는 사용자이면 다음으로 넘기고, 사용자 id 를 인증 주체로 둔다.")
        @Test
        void passesWithUserIdAsPrincipal_whenUserExists() throws Exception {
            MockHttpServletResponse response = doFilter("GET", "/api/v1/points", String.valueOf(EXISTING_USER_ID));

            assertThat(passed).isTrue();
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(passedAuthentication).isNotNull();
            assertThat(passedAuthentication.isAuthenticated()).isTrue();
            assertThat(passedAuthentication.getPrincipal()).isEqualTo(EXISTING_USER_ID);
        }
    }

    @DisplayName("식별이 필요 없는 경로(카탈로그)이면,")
    @Nested
    class OtherPath {
        @DisplayName("헤더가 없거나 이상해도 읽지 않고 그대로 넘기며, 인증 주체를 두지 않는다.")
        @ParameterizedTest
        @CsvSource(value = {
            "/api/v1/products, NULL",
            "/api/v1/products/1, abc",
            "/api/v1/brands/1, 999",
        }, nullValues = "NULL")
        void passesWithoutReadingHeader(String uri, String header) throws Exception {
            MockHttpServletResponse response = doFilter("GET", uri, header);

            assertThat(passed).isTrue();
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(passedAuthentication).isNull();
        }
    }

    private static class FakeUserRepository implements UserRepository {
        private final Set<Long> existingIds;

        FakeUserRepository(Set<Long> existingIds) {
            this.existingIds = existingIds;
        }

        @Override
        public User save(User user) {
            throw new UnsupportedOperationException("필터는 사용자를 저장하지 않는다.");
        }

        @Override
        public Optional<User> find(Long id) {
            return existingIds.contains(id) ? Optional.of(User.create()) : Optional.empty();
        }
    }
}
