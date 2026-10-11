package com.loopers.config;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    private static final String ADMIN_BRANDS = "/api-admin/v1/brands";
    private static final String USER_ID_HEADER = "X-USER-ID";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("관리자 경로에 접근할 때,")
    @Nested
    class AdminPath {
        @DisplayName("식별 없는 요청은 403 FORBIDDEN 으로 ApiResponse 형식으로 거절한다.")
        @Test
        void rejectsWithForbidden_whenRequestIsAnonymous() throws Exception {
            mvc.perform(get(ADMIN_BRANDS))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.meta.result").value("FAIL"))
                .andExpect(jsonPath("$.meta.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.meta.message").value("접근 권한이 없습니다."));
        }

        @DisplayName("관리자 역할이 없는 사용자는 403 FORBIDDEN 으로 ApiResponse 형식으로 거절한다.")
        @Test
        void rejectsWithForbidden_whenUserIsNotAdmin() throws Exception {
            mvc.perform(get(ADMIN_BRANDS).with(user("customer").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.meta.errorCode").value("FORBIDDEN"));
        }

        @DisplayName("거절한 변경 요청은 DB 를 바꾸지 않는다.")
        @Test
        void keepsDatabase_whenModifyingRequestIsRejected() throws Exception {
            // act
            mvc.perform(post(ADMIN_BRANDS)
                    .with(user("customer").roles("USER"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"루퍼스\"}"))
                .andExpect(status().isForbidden());
            mvc.perform(post(ADMIN_BRANDS)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"루퍼스\"}"))
                .andExpect(status().isForbidden());

            // assert
            assertThat(brandRepository.findAllActive(PageRequest.of(0, 10)).getTotalElements()).isZero();
        }
    }

    @DisplayName("고객 경로에 접근할 때,")
    @Nested
    class CustomerPath {
        private User saveUser() {
            return userRepository.save(User.create());
        }

        private Product saveProduct() {
            Brand brand = brandRepository.save(Brand.create("루퍼스"));
            return productRepository.save(Product.create(brand.getId(), "러닝화", new Money(89_000), new Stock(10)));
        }

        private MockHttpServletRequestBuilder customerRequest(String method, String uri, String userIdHeader) {
            MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 1000}");
            return userIdHeader == null ? builder : builder.header(USER_ID_HEADER, userIdHeader);
        }

        @DisplayName("식별이 필요한 API 는 X-USER-ID 헤더가 없으면 400 INVALID_INPUT 이다.")
        @ParameterizedTest
        @CsvSource({
            "POST, /api/v1/points/charge",
            "GET, /api/v1/points",
            "POST, /api/v1/products/1/likes",
            "DELETE, /api/v1/products/1/likes",
            "GET, /api/v1/users/1/likes",
        })
        void rejectsWithInvalidInput_whenHeaderIsMissing(String method, String uri) throws Exception {
            mvc.perform(customerRequest(method, uri, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.meta.errorCode").value("INVALID_INPUT"));
        }

        @DisplayName("식별이 필요한 API 는 존재하지 않는 사용자이면 404 NOT_FOUND 이다.")
        @ParameterizedTest
        @CsvSource({
            "POST, /api/v1/points/charge",
            "GET, /api/v1/points",
            "POST, /api/v1/products/1/likes",
            "DELETE, /api/v1/products/1/likes",
            "GET, /api/v1/users/999/likes",
        })
        void rejectsWithNotFound_whenUserDoesNotExist(String method, String uri) throws Exception {
            mvc.perform(customerRequest(method, uri, "999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.meta.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.meta.message").value("존재하지 않는 사용자입니다."));
        }

        @DisplayName("변경 요청도 CSRF 토큰 없이 처리한다.")
        @Test
        void acceptsModifyingRequest_withoutCsrfToken() throws Exception {
            // arrange
            User user = saveUser();
            Product product = saveProduct();

            // act
            mvc.perform(post("/api/v1/products/" + product.getId() + "/likes")
                    .header(USER_ID_HEADER, String.valueOf(user.getId())))
                .andExpect(status().isOk());

            // assert
            assertThat(likeRepository.exists(user.getId(), product.getId())).isTrue();
        }

        @DisplayName("카탈로그 조회는 X-USER-ID 헤더가 없거나 이상해도 읽지 않고 정상 처리한다.")
        @ParameterizedTest
        @ValueSource(strings = {"", "abc", "999"})
        void servesCatalog_regardlessOfHeader(String header) throws Exception {
            // arrange
            Brand brand = brandRepository.save(Brand.create("루퍼스"));
            MockHttpServletRequestBuilder builder = get("/api/v1/brands/" + brand.getId());
            if (!header.isEmpty()) {
                builder.header(USER_ID_HEADER, header);
            }

            // act & assert
            mvc.perform(builder)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("루퍼스"));
        }
    }
}
