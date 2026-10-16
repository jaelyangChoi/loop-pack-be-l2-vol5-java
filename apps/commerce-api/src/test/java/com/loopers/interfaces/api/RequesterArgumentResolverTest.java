package com.loopers.interfaces.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequesterArgumentResolverTest {

    private static final String MISSING_MESSAGE =
        "요청자 식별 정보가 없습니다. 식별이 필요한 경로로 등록됐는지 확인하세요.";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 스프링 컨텍스트 없이 리졸버와 예외 변환만 확인한다. 보안 컨텍스트는 테스트마다 직접 채운다.
        mockMvc = MockMvcBuilders.standaloneSetup(new RequesterTestController())
            .setCustomArgumentResolvers(new RequesterArgumentResolver())
            .setControllerAdvice(new ApiControllerAdvice())
            .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Object principal) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            principal, null, AuthorityUtils.NO_AUTHORITIES));
        SecurityContextHolder.setContext(context);
    }

    @DisplayName("지원 대상은 @Requester 가 붙은 Long 파라미터뿐이다.")
    @Test
    void supportsOnlyAnnotatedLongParameter() throws Exception {
        RequesterArgumentResolver resolver = new RequesterArgumentResolver();

        assertThat(resolver.supportsParameter(parameterOf("annotated"))).isTrue();
        assertThat(resolver.supportsParameter(parameterOf("notAnnotated"))).isFalse();
        assertThat(resolver.supportsParameter(parameterOf("annotatedString"))).isFalse();
    }

    @DisplayName("식별 필터가 둔 인증 주체(Long)를 컨트롤러에 전달한다.")
    @Test
    void passesRequesterId_whenPrincipalIsLong() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(get("/test/requester"))
            .andExpect(status().isOk())
            .andExpect(content().string("7"));
    }

    @DisplayName("인증 정보가 없으면(식별 경로 등록 누락) 500 INTERNAL_ERROR 와 원인 메시지로 거절한다.")
    @Test
    void rejectsWithInternalError_whenAuthenticationIsMissing() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/test/requester"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.meta.errorCode").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.meta.message").value(MISSING_MESSAGE));
    }

    @DisplayName("익명 사용자(주체가 Long 이 아님)여도 같은 오류로 거절한다.")
    @Test
    void rejectsWithInternalError_whenPrincipalIsAnonymous() throws Exception {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new AnonymousAuthenticationToken(
            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        SecurityContextHolder.setContext(context);

        mockMvc.perform(get("/test/requester"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.meta.errorCode").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.meta.message").value(MISSING_MESSAGE));
    }

    private MethodParameter parameterOf(String method) throws NoSuchMethodException {
        Class<?> type = method.equals("annotatedString") ? String.class : Long.class;
        return new MethodParameter(RequesterTestController.class.getDeclaredMethod(method, type), 0);
    }

    @RestController
    static class RequesterTestController {
        @GetMapping("/test/requester")
        public String get(@Requester Long userId) {
            return String.valueOf(userId);
        }

        public void annotated(@Requester Long userId) {}

        public void notAnnotated(Long userId) {}

        public void annotatedString(@Requester String userId) {}
    }
}
