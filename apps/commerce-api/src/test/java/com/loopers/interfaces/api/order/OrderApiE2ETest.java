package com.loopers.interfaces.api.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderApiE2ETest {

    private static final String ORDERS_URL = "/api/v1/orders";
    private static final String USER_ID_HEADER = "X-USER-ID";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private User saveUser() {
        return userRepository.save(User.create());
    }

    private Product saveProduct(String name, long price) {
        return productRepository.save(Product.create(1L, name, new Money(price), new Stock(10)));
    }

    private Product saveDeletedProduct() {
        Product product = Product.create(1L, "삭제됨", new Money(1_000), new Stock(1));
        product.delete();
        return productRepository.save(product);
    }

    private int orderCount() {
        return jdbcTemplate.queryForObject("select count(*) from orders", Integer.class);
    }

    private HttpHeaders headers(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (userId != null) {
            headers.set(USER_ID_HEADER, userId);
        }
        return headers;
    }

    private ResponseEntity<ApiResponse<OrderDto.OrderResponse>> create(String userId, String jsonBody) {
        return testRestTemplate.exchange(
            ORDERS_URL, HttpMethod.POST, new HttpEntity<>(jsonBody, headers(userId)),
            new ParameterizedTypeReference<>() {}
        );
    }

    private String body(Object... productIdAndQuantity) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < productIdAndQuantity.length; i += 2) {
            if (i > 0) {
                items.append(",");
            }
            items.append("{\"productId\":").append(productIdAndQuantity[i])
                .append(",\"quantity\":").append(productIdAndQuantity[i + 1]).append("}");
        }
        return "{\"items\":[" + items + "]}";
    }

    private void assertFail(ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response, HttpStatus status, String errorCode) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().meta().errorCode()).isEqualTo(errorCode);
        assertThat(orderCount()).isZero();
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class Create {
        @DisplayName("여러 품목으로 주문하면 DRAFT 주문과 품목·단가·금액·합계를 반환한다.")
        @Test
        void returnsDraftOrder_whenItemsAreValid() {
            // arrange: 4-1 시나리오. 3,000 × 1 + 2,000 × 2 = 7,000
            User user = saveUser();
            Product shirt = saveProduct("티셔츠", 3_000);
            Product socks = saveProduct("양말", 2_000);

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response =
                create(String.valueOf(user.getId()), body(shirt.getId(), 1, socks.getId(), 2));

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            OrderDto.OrderResponse order = response.getBody().data();
            assertThat(order.id()).isNotNull();
            assertThat(order.status()).isEqualTo("DRAFT");
            assertThat(order.totalAmount()).isEqualTo(7_000L);
            assertThat(order.items()).hasSize(2);
            assertThat(order.items().get(0))
                .isEqualTo(new OrderDto.ItemResponse(shirt.getId(), "티셔츠", 1, 3_000L, 3_000L));
            assertThat(order.items().get(1))
                .isEqualTo(new OrderDto.ItemResponse(socks.getId(), "양말", 2, 2_000L, 4_000L));
            assertThat(orderCount()).isEqualTo(1);
        }

        @DisplayName("X-USER-ID 헤더가 없으면 400 INVALID_INPUT 이다 (식별 경로 등록 확인).")
        @Test
        void returnsBadRequest_whenHeaderIsMissing() {
            Product shirt = saveProduct("티셔츠", 3_000);

            assertFail(create(null, body(shirt.getId(), 1)), HttpStatus.BAD_REQUEST, "INVALID_INPUT");
        }

        @DisplayName("X-USER-ID 헤더가 숫자가 아니면 400 INVALID_INPUT 이다.")
        @Test
        void returnsBadRequest_whenHeaderIsNotNumber() {
            Product shirt = saveProduct("티셔츠", 3_000);

            assertFail(create("abc", body(shirt.getId(), 1)), HttpStatus.BAD_REQUEST, "INVALID_INPUT");
        }

        @DisplayName("존재하지 않는 사용자이면 404 NOT_FOUND 이다.")
        @Test
        void returnsNotFound_whenUserDoesNotExist() {
            Product shirt = saveProduct("티셔츠", 3_000);

            assertFail(create("999", body(shirt.getId(), 1)), HttpStatus.NOT_FOUND, "NOT_FOUND");
        }

        @DisplayName("본문의 필수 값이 빠지거나 형식이 틀리면 400 INVALID_INPUT 이다.")
        @ParameterizedTest
        @ValueSource(strings = {
            "{}",
            "{\"items\":null}",
            "{\"items\":[null]}",
            "{\"items\":[{\"quantity\":1}]}",
            "{\"items\":[{\"productId\":1}]}",
            "{\"items\":[{\"productId\":1,\"quantity\":1.5}]}",
            "{\"items\":[{\"productId\":1,\"quantity\":\"abc\"}]}",
            "{\"items\":[{\"productId\":\"abc\",\"quantity\":1}]}",
        })
        void returnsBadRequest_whenBodyIsMalformed(String json) {
            User user = saveUser();
            saveProduct("티셔츠", 3_000);

            assertFail(create(String.valueOf(user.getId()), json), HttpStatus.BAD_REQUEST, "INVALID_INPUT");
        }

        @DisplayName("수량이 int 범위를 넘으면 400 INVALID_INPUT 이고, 메시지에 몇 번째 품목의 어떤 필드인지 담는다.")
        @Test
        void returnsBadRequest_withItemPath_whenQuantityOverflows() {
            User user = saveUser();
            Product shirt = saveProduct("티셔츠", 3_000);

            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response =
                create(String.valueOf(user.getId()), body(shirt.getId(), 1, shirt.getId() + 1, 3_000_000_000L));

            assertFail(response, HttpStatus.BAD_REQUEST, "INVALID_INPUT");
            assertThat(response.getBody().meta().message()).contains("items[1].quantity", "3000000000");
        }

        @DisplayName("품목이 비었거나 수량이 0 이하이면 400 INVALID_INPUT 이다.")
        @ParameterizedTest
        @ValueSource(strings = {"{\"items\":[]}", "0", "-1"})
        void returnsBadRequest_whenItemsAreEmptyOrQuantityIsNotPositive(String value) {
            User user = saveUser();
            Product shirt = saveProduct("티셔츠", 3_000);
            String json = value.startsWith("{") ? value : body(shirt.getId(), value);

            assertFail(create(String.valueOf(user.getId()), json), HttpStatus.BAD_REQUEST, "INVALID_INPUT");
        }

        @DisplayName("같은 상품이 두 품목으로 들어오면 400 INVALID_INPUT 이고 저장하지 않는다.")
        @Test
        void returnsBadRequest_whenProductIsDuplicated() {
            User user = saveUser();
            Product shirt = saveProduct("티셔츠", 3_000);

            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response =
                create(String.valueOf(user.getId()), body(shirt.getId(), 1, shirt.getId(), 2));

            assertFail(response, HttpStatus.BAD_REQUEST, "INVALID_INPUT");
            assertThat(response.getBody().meta().message()).contains("중복");
        }

        @DisplayName("없거나 삭제된 상품이 있으면 404 NOT_FOUND 이고 메시지에 상품 id 를 담는다.")
        @Test
        void returnsNotFound_whenProductIsMissingOrDeleted() {
            User user = saveUser();
            Product shirt = saveProduct("티셔츠", 3_000);
            Product deleted = saveDeletedProduct();

            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = create(
                String.valueOf(user.getId()), body(shirt.getId(), 1, deleted.getId(), 1, 999_999, 1)
            );

            assertFail(response, HttpStatus.NOT_FOUND, "NOT_FOUND");
            assertThat(response.getBody().meta().message()).contains("상품 ID: " + deleted.getId() + ", 999999");
        }

        @DisplayName("값 규칙 위반과 없는 상품이 함께 있으면 400 INVALID_INPUT 이 먼저다.")
        @Test
        void returnsBadRequest_beforeNotFound() {
            User user = saveUser();

            assertFail(create(String.valueOf(user.getId()), body(999_999, 0)), HttpStatus.BAD_REQUEST, "INVALID_INPUT");
        }
    }
}
