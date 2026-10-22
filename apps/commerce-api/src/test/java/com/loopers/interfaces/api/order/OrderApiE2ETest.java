package com.loopers.interfaces.api.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.point.PointDto;
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
        return saveProduct(name, price, 10);
    }

    private Product saveProduct(String name, long price, int stock) {
        return productRepository.save(Product.create(1L, name, new Money(price), new Stock(stock)));
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

    private ResponseEntity<ApiResponse<OrderDto.OrderResponse>> getDetail(String userId, Object orderId) {
        return testRestTemplate.exchange(
            ORDERS_URL + "/" + orderId, HttpMethod.GET, new HttpEntity<>(headers(userId)),
            new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<OrderDto.OrderSummaryResponse>>> getList(String userId, String query) {
        return testRestTemplate.exchange(
            ORDERS_URL + query, HttpMethod.GET, new HttpEntity<>(headers(userId)),
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
            assertThat(order.payment()).isNull();
            assertThat(order.orderedAt()).isNotNull();
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

        @DisplayName("본문의 필수 값이 빠지거나 형식이 틀리면 400 INVALID_INPUT 이다.")
        @ParameterizedTest
        @ValueSource(strings = {
            "{}",
            "{\"items\":[null]}",
            "{\"items\":[{\"quantity\":1}]}",
            "{\"items\":[{\"productId\":1,\"quantity\":1.5}]}",
            "{\"items\":[{\"productId\":1,\"quantity\":\"abc\"}]}",
        })
        void returnsBadRequest_whenBodyIsMalformed(String json) {
            User user = saveUser();
            saveProduct("티셔츠", 3_000);

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

        @DisplayName("재고가 0이거나 요청 수량보다 적으면 409 OUT_OF_STOCK 이고 저장하지 않는다.")
        @Test
        void returnsConflict_whenStockIsSoldOutOrShort() {
            User user = saveUser();
            Product soldOut = saveProduct("품절 상품", 3_000, 0);
            Product few = saveProduct("재고 3개", 3_000, 3);

            assertFail(create(String.valueOf(user.getId()), body(soldOut.getId(), 1)), HttpStatus.CONFLICT, "OUT_OF_STOCK");
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response =
                create(String.valueOf(user.getId()), body(few.getId(), 5));

            assertFail(response, HttpStatus.CONFLICT, "OUT_OF_STOCK");
            assertThat(response.getBody().meta().message()).contains("재고 3개");
        }

        @DisplayName("값 규칙 위반과 없는 상품이 함께 있으면 400 INVALID_INPUT 이 먼저다.")
        @Test
        void returnsBadRequest_beforeNotFound() {
            User user = saveUser();

            assertFail(create(String.valueOf(user.getId()), body(999_999, 0)), HttpStatus.BAD_REQUEST, "INVALID_INPUT");
        }
    }

    @DisplayName("POST /api/v1/orders/{orderId}/confirm")
    @Nested
    class Confirm {
        private User saveUserWithBalance(long amount) {
            User user = User.create();
            user.chargePoint(new Money(amount));
            return userRepository.save(user);
        }

        private Long placeOrder(User user, Object... productIdAndQuantity) {
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response =
                create(String.valueOf(user.getId()), body(productIdAndQuantity));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            return response.getBody().data().id();
        }

        private ResponseEntity<ApiResponse<OrderDto.OrderResponse>> confirm(String userId, Object orderId) {
            return testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId + "/confirm", HttpMethod.POST, new HttpEntity<>(headers(userId)),
                new ParameterizedTypeReference<>() {}
            );
        }

        private long balanceOf(User user) {
            return userRepository.find(user.getId()).orElseThrow().getPointBalance().balance().amount();
        }

        private int stockOf(Product product) {
            return jdbcTemplate.queryForObject("select stock from products where id = ?", Integer.class, product.getId());
        }

        private String statusOf(Long orderId) {
            return jdbcTemplate.queryForObject("select status from orders where id = ?", String.class, orderId);
        }

        // 확정이 거절되면 주문은 DRAFT 로 남고 재고·잔액은 그대로여야 한다.
        private void assertRejected(
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response, HttpStatus status, String errorCode,
            Long orderId, User user, long balance
        ) {
            assertThat(response.getStatusCode()).isEqualTo(status);
            assertThat(response.getBody().meta().errorCode()).isEqualTo(errorCode);
            assertThat(statusOf(orderId)).isEqualTo("DRAFT");
            assertThat(balanceOf(user)).isEqualTo(balance);
        }

        @DisplayName("충전 → 주문 생성 → 확정 → 잔액 조회: 결제 결과가 담긴 CONFIRMED 주문을 반환하고, 재고와 잔액이 반영된다.")
        @Test
        void returnsConfirmedOrder_andReflectsStockAndBalance() {
            // arrange: 잔액 0에서 10,000 충전(API), 3,000×1 + 2,000×2 = 7,000 주문
            User user = saveUser();
            String userId = String.valueOf(user.getId());
            HttpHeaders chargeHeaders = headers(userId);
            testRestTemplate.exchange(
                "/api/v1/points/charge", HttpMethod.POST, new HttpEntity<>("{\"amount\": 10000}", chargeHeaders),
                new ParameterizedTypeReference<ApiResponse<PointDto.PointResponse>>() {}
            );
            Product shirt = saveProduct("티셔츠", 3_000);
            Product socks = saveProduct("양말", 2_000);
            Long orderId = placeOrder(user, shirt.getId(), 1, socks.getId(), 2);

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = confirm(userId, orderId);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            OrderDto.OrderResponse order = response.getBody().data();
            assertThat(order.id()).isEqualTo(orderId);
            assertThat(order.status()).isEqualTo("CONFIRMED");
            assertThat(order.totalAmount()).isEqualTo(7_000L);
            assertThat(order.payment()).isEqualTo(new OrderDto.PaymentResponse(7_000L));
            assertThat(order.items()).hasSize(2);
            assertThat(stockOf(shirt)).isEqualTo(9);
            assertThat(stockOf(socks)).isEqualTo(8);
            ResponseEntity<ApiResponse<PointDto.PointResponse>> balance = testRestTemplate.exchange(
                "/api/v1/points", HttpMethod.GET, new HttpEntity<>(headers(userId)),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(balance.getBody().data().balance()).isEqualTo(3_000L);

            // 내 주문 상세·목록에서도 CONFIRMED 와 결제액이 조회된다.
            OrderDto.OrderResponse detail = getDetail(userId, orderId).getBody().data();
            assertThat(detail.status()).isEqualTo("CONFIRMED");
            assertThat(detail.payment()).isEqualTo(new OrderDto.PaymentResponse(7_000L));
            OrderDto.OrderSummaryResponse listed = getList(userId, "").getBody().data().content().get(0);
            assertThat(listed.id()).isEqualTo(orderId);
            assertThat(listed.status()).isEqualTo("CONFIRMED");
            assertThat(listed.payment()).isEqualTo(new OrderDto.PaymentResponse(7_000L));
        }

        @DisplayName("잔액이 부족하면 409 INSUFFICIENT_POINT 이고, 주문·재고·잔액이 그대로다 (409 업무 오류 4종의 대표, 나머지는 OrderFacadeTest).")
        @Test
        void returnsConflict_whenBalanceIsInsufficient() {
            User user = saveUserWithBalance(1_000);
            Product shirt = saveProduct("티셔츠", 3_000);
            Long orderId = placeOrder(user, shirt.getId(), 1);

            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = confirm(String.valueOf(user.getId()), orderId);

            assertRejected(response, HttpStatus.CONFLICT, "INSUFFICIENT_POINT", orderId, user, 1_000);
            assertThat(stockOf(shirt)).isEqualTo(10);
        }

        @DisplayName("없는 주문과 다른 사용자의 주문은 404 NOT_FOUND 이다.")
        @Test
        void returnsNotFound_whenOrderIsMissingOrBelongsToOtherUser() {
            User owner = saveUserWithBalance(10_000);
            User other = saveUserWithBalance(10_000);
            Product shirt = saveProduct("티셔츠", 3_000);
            Long orderId = placeOrder(owner, shirt.getId(), 1);

            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> missing = confirm(String.valueOf(owner.getId()), 999_999);
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> otherUsers = confirm(String.valueOf(other.getId()), orderId);

            assertRejected(missing, HttpStatus.NOT_FOUND, "NOT_FOUND", orderId, owner, 10_000);
            assertRejected(otherUsers, HttpStatus.NOT_FOUND, "NOT_FOUND", orderId, other, 10_000);
            assertThat(otherUsers.getBody().meta().message()).isEqualTo(missing.getBody().meta().message());
        }

        @DisplayName("orderId 가 숫자가 아니면 400 INVALID_INPUT 이다.")
        @Test
        void returnsBadRequest_whenOrderIdIsNotNumber() {
            User user = saveUserWithBalance(10_000);

            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = confirm(String.valueOf(user.getId()), "abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().meta().errorCode()).isEqualTo("INVALID_INPUT");
        }

        @DisplayName("X-USER-ID 헤더가 없으면 400 INVALID_INPUT 이다 (식별 경로 등록 확인).")
        @Test
        void returnsBadRequest_whenHeaderIsMissing() {
            User user = saveUserWithBalance(10_000);
            Product shirt = saveProduct("티셔츠", 3_000);
            Long orderId = placeOrder(user, shirt.getId(), 1);

            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = confirm(null, orderId);

            assertRejected(response, HttpStatus.BAD_REQUEST, "INVALID_INPUT", orderId, user, 10_000);
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    class GetList {
        @DisplayName("내 주문만 최신순으로 요약(품목 수, 결제 결과, 주문 시각)해서 반환한다.")
        @Test
        void returnsMyOrdersSummary_newestFirst() {
            // arrange
            User me = saveUser();
            User other = saveUser();
            Product shirt = saveProduct("티셔츠", 3_000);
            create(String.valueOf(me.getId()), body(shirt.getId(), 1));
            create(String.valueOf(other.getId()), body(shirt.getId(), 1));
            create(String.valueOf(me.getId()), body(shirt.getId(), 2));

            // act
            ResponseEntity<ApiResponse<PageResponse<OrderDto.OrderSummaryResponse>>> response =
                getList(String.valueOf(me.getId()), "");

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            PageResponse<OrderDto.OrderSummaryResponse> page = response.getBody().data();
            assertThat(page.totalElements()).isEqualTo(2);
            assertThat(page.content()).extracting(OrderDto.OrderSummaryResponse::totalAmount)
                .containsExactly(6_000L, 3_000L);
            OrderDto.OrderSummaryResponse newest = page.content().get(0);
            assertThat(newest.status()).isEqualTo("DRAFT");
            assertThat(newest.itemCount()).isEqualTo(1);
            assertThat(newest.payment()).isNull();
            assertThat(newest.orderedAt()).isNotNull();
        }

        @DisplayName("page·size 로 나누어 조회한다.")
        @Test
        void paginates() {
            User me = saveUser();
            Product shirt = saveProduct("티셔츠", 3_000);
            for (int i = 0; i < 3; i++) {
                create(String.valueOf(me.getId()), body(shirt.getId(), 1));
            }

            PageResponse<OrderDto.OrderSummaryResponse> page =
                getList(String.valueOf(me.getId()), "?page=1&size=2").getBody().data();

            assertThat(page.content()).hasSize(1);
            assertThat(page.page()).isEqualTo(1);
            assertThat(page.size()).isEqualTo(2);
            assertThat(page.totalElements()).isEqualTo(3);
            assertThat(page.totalPages()).isEqualTo(2);
        }

        @DisplayName("size 가 범위를 벗어나면 400 INVALID_INPUT 이다 (page·size 검증의 대표).")
        @Test
        void returnsBadRequest_whenSizeIsOutOfRange() {
            User me = saveUser();

            ResponseEntity<ApiResponse<PageResponse<OrderDto.OrderSummaryResponse>>> response =
                getList(String.valueOf(me.getId()), "?size=101");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().meta().errorCode()).isEqualTo("INVALID_INPUT");
        }

        @DisplayName("X-USER-ID 헤더가 없으면 400 INVALID_INPUT 이다 (식별 경로 등록 확인).")
        @Test
        void returnsBadRequest_whenHeaderIsMissing() {
            ResponseEntity<ApiResponse<PageResponse<OrderDto.OrderSummaryResponse>>> response = getList(null, "");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().meta().errorCode()).isEqualTo("INVALID_INPUT");
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    class GetDetail {
        @DisplayName("내 주문의 품목·합계·상태·주문 시각을 반환한다.")
        @Test
        void returnsMyOrderDetail() {
            // arrange
            User me = saveUser();
            Product shirt = saveProduct("티셔츠", 3_000);
            Product socks = saveProduct("양말", 2_000);
            Long orderId = create(String.valueOf(me.getId()), body(shirt.getId(), 1, socks.getId(), 2))
                .getBody().data().id();

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = getDetail(String.valueOf(me.getId()), orderId);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            OrderDto.OrderResponse order = response.getBody().data();
            assertThat(order.id()).isEqualTo(orderId);
            assertThat(order.status()).isEqualTo("DRAFT");
            assertThat(order.totalAmount()).isEqualTo(7_000L);
            assertThat(order.items()).hasSize(2);
            assertThat(order.items().get(1))
                .isEqualTo(new OrderDto.ItemResponse(socks.getId(), "양말", 2, 2_000L, 4_000L));
            assertThat(order.payment()).isNull();
            assertThat(order.orderedAt()).isNotNull();
        }

        @DisplayName("다른 사용자의 주문과 없는 주문은 404 NOT_FOUND 이다.")
        @Test
        void returnsNotFound_whenOrderIsMissingOrBelongsToOtherUser() {
            User owner = saveUser();
            User other = saveUser();
            Product shirt = saveProduct("티셔츠", 3_000);
            Long orderId = create(String.valueOf(owner.getId()), body(shirt.getId(), 1)).getBody().data().id();

            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> otherUsers = getDetail(String.valueOf(other.getId()), orderId);
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> missing = getDetail(String.valueOf(owner.getId()), 999_999);

            assertThat(otherUsers.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(otherUsers.getBody().meta().message()).isEqualTo(missing.getBody().meta().message());
        }

        @DisplayName("orderId 가 숫자가 아니면 400 INVALID_INPUT 이다.")
        @Test
        void returnsBadRequest_whenOrderIdIsNotNumber() {
            User me = saveUser();

            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = getDetail(String.valueOf(me.getId()), "abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().meta().errorCode()).isEqualTo("INVALID_INPUT");
        }

        @DisplayName("X-USER-ID 헤더가 없으면 400 INVALID_INPUT 이다 (식별 경로 등록 확인).")
        @Test
        void returnsBadRequest_whenHeaderIsMissing() {
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = getDetail(null, 1);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().meta().errorCode()).isEqualTo("INVALID_INPUT");
        }
    }
}
