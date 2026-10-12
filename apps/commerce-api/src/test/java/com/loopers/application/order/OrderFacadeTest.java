package com.loopers.application.order;

import com.loopers.application.product.AdminProductFacade;
import com.loopers.domain.common.Money;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeTest {

    private static final long MAX_PRICE = 1_000_000_000L;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private AdminProductFacade adminProductFacade;

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

    private Product saveProduct(String name, long price, int stock) {
        return productRepository.save(Product.create(1L, name, new Money(price), new Stock(stock)));
    }

    private Product saveDeletedProduct(String name) {
        Product product = Product.create(1L, name, new Money(1_000), new Stock(1));
        product.delete();
        return productRepository.save(product);
    }

    private int orderCount() {
        return jdbcTemplate.queryForObject("select count(*) from orders", Integer.class);
    }

    private CoreException rejected(Runnable action) {
        return assertThrows(CoreException.class, action::run);
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class Create {
        @DisplayName("DRAFT 주문이 저장되고, 품목·단가·합계가 응답에 담긴다.")
        @Test
        void savesDraftOrder_withItemsAndTotal() {
            // arrange: 4-1 시나리오. 3,000 × 1 + 2,000 × 2 = 7,000
            User user = saveUser();
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            Product socks = saveProduct("양말", 2_000, 10);

            // act
            OrderInfo info = orderFacade.create(
                user.getId(),
                List.of(new OrderItemCommand(shirt.getId(), 1), new OrderItemCommand(socks.getId(), 2))
            );

            // assert
            assertThat(info.id()).isNotNull();
            assertThat(info.userId()).isEqualTo(user.getId());
            assertThat(info.status()).isEqualTo(OrderStatus.DRAFT);
            assertThat(info.totalAmount()).isEqualTo(7_000L);
            assertThat(info.items()).hasSize(2);
            assertThat(info.items().get(0)).isEqualTo(new OrderItemInfo(shirt.getId(), "티셔츠", 1, 3_000L, 3_000L));
            assertThat(info.items().get(1)).isEqualTo(new OrderItemInfo(socks.getId(), "양말", 2, 2_000L, 4_000L));
            assertThat(orderCount()).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                "select status from orders where id = ?", String.class, info.id()
            )).isEqualTo("DRAFT");
        }

        @DisplayName("재고와 포인트 잔액은 차감하지 않는다.")
        @Test
        void doesNotDeductStockOrPoint() {
            // arrange
            User user = saveUser();
            user.chargePoint(new Money(10_000));
            userRepository.save(user);
            Product shirt = saveProduct("티셔츠", 3_000, 10);

            // act
            orderFacade.create(user.getId(), List.of(new OrderItemCommand(shirt.getId(), 3)));

            // assert: 저장소에서 다시 읽어 확인한다.
            assertThat(productRepository.findActive(shirt.getId()).orElseThrow().getStock()).isEqualTo(new Stock(10));
            assertThat(userRepository.find(user.getId()).orElseThrow().getPointBalance().balance())
                .isEqualTo(new Money(10_000));
        }

        @DisplayName("재고가 요청 수량과 같으면 주문을 만들 수 있고, 재고는 차감하지 않는다.")
        @Test
        void savesDraftOrder_whenStockEqualsQuantity() {
            // arrange
            User user = saveUser();
            Product shirt = saveProduct("티셔츠", 3_000, 5);

            // act
            OrderInfo info = orderFacade.create(user.getId(), List.of(new OrderItemCommand(shirt.getId(), 5)));

            // assert
            assertThat(info.status()).isEqualTo(OrderStatus.DRAFT);
            assertThat(productRepository.findActive(shirt.getId()).orElseThrow().getStock()).isEqualTo(new Stock(5));
        }

        @DisplayName("재고가 0이거나 요청 수량보다 적으면 OUT_OF_STOCK 이고 저장하지 않는다.")
        @Test
        void throwsOutOfStock_whenStockIsSoldOutOrShort() {
            // arrange
            User user = saveUser();
            Product soldOut = saveProduct("품절 상품", 3_000, 0);
            Product few = saveProduct("재고 3개", 3_000, 3);

            // act
            CoreException soldOutException = rejected(
                () -> orderFacade.create(user.getId(), List.of(new OrderItemCommand(soldOut.getId(), 1)))
            );
            CoreException shortException = rejected(
                () -> orderFacade.create(user.getId(), List.of(new OrderItemCommand(few.getId(), 5)))
            );

            // assert
            assertThat(soldOutException.getErrorType()).isEqualTo(ErrorType.OUT_OF_STOCK);
            assertThat(shortException.getErrorType()).isEqualTo(ErrorType.OUT_OF_STOCK);
            assertThat(orderCount()).isZero();
            assertThat(productRepository.findActive(few.getId()).orElseThrow().getStock()).isEqualTo(new Stock(3));
        }

        @DisplayName("재고가 부족한 상품이 여럿이면 메시지에 상품명을 모두 담고, 충분한 상품은 담지 않는다.")
        @Test
        void throwsOutOfStock_withAllShortProductNames() {
            // arrange
            User user = saveUser();
            Product shirt = saveProduct("티셔츠", 3_000, 1);
            Product socks = saveProduct("양말", 2_000, 10);
            Product hat = saveProduct("모자", 5_000, 0);

            // act
            CoreException exception = rejected(() -> orderFacade.create(
                user.getId(),
                List.of(
                    new OrderItemCommand(shirt.getId(), 2),
                    new OrderItemCommand(socks.getId(), 1),
                    new OrderItemCommand(hat.getId(), 1)
                )
            ));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.OUT_OF_STOCK);
            assertThat(exception.getMessage()).contains("재고가 부족합니다.").contains("(티셔츠, 모자)");
            assertThat(exception.getMessage()).doesNotContain("양말");
        }

        @DisplayName("없는 상품과 재고 부족이 함께 있으면 NOT_FOUND 가 먼저이고, 값 규칙 위반과 함께 있으면 INVALID_INPUT 이 먼저다.")
        @Test
        void checksExistenceAndValueRules_beforeStock() {
            // arrange
            User user = saveUser();
            Product soldOut = saveProduct("품절 상품", 3_000, 0);

            // act & assert
            assertThat(rejected(() -> orderFacade.create(
                user.getId(),
                List.of(new OrderItemCommand(soldOut.getId(), 1), new OrderItemCommand(999_999L, 1))
            )).getErrorType()).as("재고 부족 + 없는 상품").isEqualTo(ErrorType.NOT_FOUND);
            assertThat(rejected(() -> orderFacade.create(
                user.getId(),
                List.of(new OrderItemCommand(soldOut.getId(), 1), new OrderItemCommand(soldOut.getId(), 1))
            )).getErrorType()).as("재고 부족 + 중복").isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(orderCount()).isZero();
        }

        @DisplayName("주문 뒤 관리자가 상품 이름·가격을 바꿔도 저장된 품목은 주문 당시 값을 유지한다.")
        @Test
        void keepsSnapshot_whenProductChangesAfterwards() {
            // arrange
            User user = saveUser();
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            OrderInfo info = orderFacade.create(user.getId(), List.of(new OrderItemCommand(shirt.getId(), 2)));

            // act
            adminProductFacade.update(shirt.getId(), "후드티", 50_000);

            // assert: 상품은 바뀌었지만 주문 품목은 그대로다.
            assertThat(productRepository.findActive(shirt.getId()).orElseThrow().getName()).isEqualTo("후드티");
            Map<String, Object> item = jdbcTemplate.queryForMap(
                "select product_name, unit_price, amount from order_items where order_id = ?", info.id()
            );
            assertThat(item.get("product_name")).isEqualTo("티셔츠");
            assertThat(((Number) item.get("unit_price")).longValue()).isEqualTo(3_000L);
            assertThat(((Number) item.get("amount")).longValue()).isEqualTo(6_000L);
            assertThat(jdbcTemplate.queryForObject(
                "select total_amount from orders where id = ?", Long.class, info.id()
            )).isEqualTo(6_000L);
        }

        @DisplayName("없는 상품이나 삭제된 상품이 있으면 NOT_FOUND 이고, 메시지에 해당 상품 id 를 모두 담고, 저장하지 않는다.")
        @Test
        void throwsNotFound_withAllMissingProductIds() {
            // arrange
            User user = saveUser();
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            Product deleted = saveDeletedProduct("삭제됨");
            long missingId = 999_999L;

            // act
            CoreException exception = rejected(() -> orderFacade.create(
                user.getId(),
                List.of(
                    new OrderItemCommand(deleted.getId(), 1),
                    new OrderItemCommand(shirt.getId(), 1),
                    new OrderItemCommand(missingId, 1)
                )
            ));

            // assert: 요청 순서대로 모든 문제 상품을 알린다.
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(exception.getMessage())
                .contains("존재하지 않는 상품이 포함되어 있습니다.")
                .contains("상품 ID: " + deleted.getId() + ", " + missingId);
            assertThat(orderCount()).isZero();
        }

        @DisplayName("값 규칙을 어기면 INVALID_INPUT 이고 저장하지 않는다.")
        @Test
        void throwsInvalidInput_whenValueRuleIsViolated() {
            // arrange
            User user = saveUser();
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            Long productId = shirt.getId();

            // act & assert
            assertThat(rejected(() -> orderFacade.create(user.getId(), List.of())).getErrorType())
                .as("빈 목록").isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(rejected(() -> orderFacade.create(user.getId(), null)).getErrorType())
                .as("목록 없음").isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(rejected(() -> orderFacade.create(
                user.getId(), Arrays.asList(new OrderItemCommand(productId, 1), null)
            )).getErrorType()).as("null 품목").isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(rejected(() -> orderFacade.create(user.getId(), List.of(new OrderItemCommand(productId, 0))))
                .getErrorType()).as("수량 0").isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(rejected(() -> orderFacade.create(user.getId(), List.of(new OrderItemCommand(productId, -1))))
                .getErrorType()).as("수량 음수").isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(rejected(() -> orderFacade.create(
                user.getId(), List.of(new OrderItemCommand(productId, 1), new OrderItemCommand(productId, 2))
            )).getErrorType()).as("중복 상품").isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(orderCount()).isZero();
        }

        @DisplayName("값 규칙 위반과 없는 상품이 함께 있으면 상품을 조회하기 전에 INVALID_INPUT 으로 거절한다.")
        @Test
        void throwsInvalidInput_beforeLookingUpProducts() {
            // arrange
            User user = saveUser();
            long missingId = 999_999L;

            // act & assert: 없는 상품이 섞여 있어도 404 가 아니라 400 이다.
            assertThat(rejected(() -> orderFacade.create(
                user.getId(), List.of(new OrderItemCommand(missingId, 0))
            )).getErrorType()).as("수량 0 + 없는 상품").isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(rejected(() -> orderFacade.create(
                user.getId(), List.of(new OrderItemCommand(missingId, 1), new OrderItemCommand(missingId, 1))
            )).getErrorType()).as("중복 + 없는 상품").isEqualTo(ErrorType.INVALID_INPUT);
        }

        @DisplayName("합계가 금액 표현 범위를 넘으면 INVALID_INPUT 이고 저장하지 않는다.")
        @Test
        void throwsInvalidInput_whenTotalOverflows() {
            // arrange: 품목 하나는 범위 안이지만 다섯 개를 더하면 long 범위를 넘는다. 재고는 수량만큼 충분해야 재고 확인을 통과한다.
            User user = saveUser();
            List<OrderItemCommand> items = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Product product = saveProduct("상품" + i, MAX_PRICE, Integer.MAX_VALUE);
                items.add(new OrderItemCommand(product.getId(), Integer.MAX_VALUE));
            }

            // act
            CoreException exception = rejected(() -> orderFacade.create(user.getId(), items));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(orderCount()).isZero();
        }

        @DisplayName("요청자 식별 정보가 없으면(등록 누락) INTERNAL_ERROR 이고 저장하지 않는다.")
        @Test
        void throwsInternalError_whenRequesterIsMissing() {
            // arrange
            Product shirt = saveProduct("티셔츠", 3_000, 10);

            // act
            CoreException exception = rejected(
                () -> orderFacade.create(null, List.of(new OrderItemCommand(shirt.getId(), 1)))
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
            assertThat(orderCount()).isZero();
        }
    }

    @DisplayName("주문을 확정할 때,")
    @Nested
    class Confirm {
        private User saveUserWithBalance(long amount) {
            User user = User.create();
            user.chargePoint(new Money(amount));
            return userRepository.save(user);
        }

        private Long placeOrder(User user, OrderItemCommand... items) {
            return orderFacade.create(user.getId(), List.of(items)).id();
        }

        // 저장소에서 다시 읽어 확인한다. 삭제된 상품도 읽을 수 있도록 SQL 로 조회한다.
        private int stockOf(Product product) {
            return jdbcTemplate.queryForObject("select stock from products where id = ?", Integer.class, product.getId());
        }

        private long balanceOf(User user) {
            return userRepository.find(user.getId()).orElseThrow().getPointBalance().balance().amount();
        }

        private String statusOf(Long orderId) {
            return jdbcTemplate.queryForObject("select status from orders where id = ?", String.class, orderId);
        }

        private void assertUntouched(Long orderId, User user, long balance, Map<Product, Integer> stocks) {
            assertThat(statusOf(orderId)).isEqualTo("DRAFT");
            assertThat(balanceOf(user)).isEqualTo(balance);
            stocks.forEach((product, stock) -> assertThat(stockOf(product)).as(product.getName()).isEqualTo(stock));
        }

        @DisplayName("재고를 차감하고 포인트를 결제해 CONFIRMED 가 되며, 결제액과 결제 후 잔액이 저장된다.")
        @Test
        void confirmsOrder_deductsStockAndPoint() {
            // arrange: 4-1 시나리오. 잔액 10,000 → 3,000×1 + 2,000×2 = 7,000 결제 → 잔액 3,000
            User user = saveUserWithBalance(10_000);
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            Product socks = saveProduct("양말", 2_000, 10);
            Long orderId = placeOrder(user, new OrderItemCommand(shirt.getId(), 1), new OrderItemCommand(socks.getId(), 2));

            // act
            OrderInfo info = orderFacade.confirm(user.getId(), orderId);

            // assert
            assertThat(info.status()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(info.totalAmount()).isEqualTo(7_000L);
            assertThat(info.payment()).isEqualTo(new PaymentInfo(7_000L, 3_000L));
            assertThat(info.items()).hasSize(2);
            assertThat(stockOf(shirt)).isEqualTo(9);
            assertThat(stockOf(socks)).isEqualTo(8);
            assertThat(balanceOf(user)).isEqualTo(3_000L);
            Map<String, Object> saved = jdbcTemplate.queryForMap(
                "select status, paid_amount, balance_after from orders where id = ?", orderId
            );
            assertThat(saved.get("status")).isEqualTo("CONFIRMED");
            assertThat(((Number) saved.get("paid_amount")).longValue()).isEqualTo(7_000L);
            assertThat(((Number) saved.get("balance_after")).longValue()).isEqualTo(3_000L);
        }

        @DisplayName("재고가 수량과 정확히 같고 잔액이 합계와 정확히 같아도 확정되며, 재고와 잔액은 0이 된다.")
        @Test
        void confirmsOrder_whenStockAndBalanceAreExactlyEnough() {
            // arrange
            User user = saveUserWithBalance(6_000);
            Product shirt = saveProduct("티셔츠", 3_000, 2);
            Long orderId = placeOrder(user, new OrderItemCommand(shirt.getId(), 2));

            // act
            OrderInfo info = orderFacade.confirm(user.getId(), orderId);

            // assert
            assertThat(info.payment()).isEqualTo(new PaymentInfo(6_000L, 0L));
            assertThat(stockOf(shirt)).isZero();
            assertThat(balanceOf(user)).isZero();
        }

        @DisplayName("주문 뒤 관리자가 가격을 바꿔도 결제액은 주문 당시 합계다.")
        @Test
        void paysOrderTotal_evenWhenPriceChangesAfterOrder() {
            // arrange
            User user = saveUserWithBalance(10_000);
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            Long orderId = placeOrder(user, new OrderItemCommand(shirt.getId(), 2));
            adminProductFacade.update(shirt.getId(), "티셔츠", 500_000);

            // act
            OrderInfo info = orderFacade.confirm(user.getId(), orderId);

            // assert
            assertThat(info.payment()).isEqualTo(new PaymentInfo(6_000L, 4_000L));
            assertThat(balanceOf(user)).isEqualTo(4_000L);
        }

        @DisplayName("없는 주문과 다른 사용자의 주문은 NOT_FOUND 이고, 아무것도 바꾸지 않는다.")
        @Test
        void throwsNotFound_whenOrderIsMissingOrBelongsToOtherUser() {
            // arrange
            User owner = saveUserWithBalance(10_000);
            User other = saveUserWithBalance(10_000);
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            Long orderId = placeOrder(owner, new OrderItemCommand(shirt.getId(), 1));

            // act
            CoreException missing = rejected(() -> orderFacade.confirm(owner.getId(), 999_999L));
            CoreException otherUsers = rejected(() -> orderFacade.confirm(other.getId(), orderId));

            // assert
            assertThat(missing.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(otherUsers.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(otherUsers.getMessage()).isEqualTo(missing.getMessage());
            assertUntouched(orderId, owner, 10_000, Map.of(shirt, 10));
            assertThat(balanceOf(other)).isEqualTo(10_000L);
        }

        @DisplayName("이미 확정된 주문은 ORDER_ALREADY_CONFIRMED 이고, 재고와 잔액을 다시 건드리지 않는다. 재고가 0이 돼도 이 오류가 먼저다.")
        @Test
        void throwsAlreadyConfirmed_withoutChangingAnything() {
            // arrange
            User user = saveUserWithBalance(10_000);
            Product shirt = saveProduct("티셔츠", 3_000, 2);
            Long orderId = placeOrder(user, new OrderItemCommand(shirt.getId(), 2));
            orderFacade.confirm(user.getId(), orderId);

            // act: 확정으로 재고가 0이 되었고 잔액도 줄었다.
            CoreException exception = rejected(() -> orderFacade.confirm(user.getId(), orderId));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.ORDER_ALREADY_CONFIRMED);
            assertThat(statusOf(orderId)).isEqualTo("CONFIRMED");
            assertThat(stockOf(shirt)).isZero();
            assertThat(balanceOf(user)).isEqualTo(4_000L);
        }

        @DisplayName("주문한 상품이 삭제됐으면 PRODUCT_UNAVAILABLE 이고, 메시지에 삭제된 상품의 주문 당시 이름을 모두 담으며, 아무것도 바꾸지 않는다.")
        @Test
        void throwsProductUnavailable_withDeletedProductNames() {
            // arrange
            User user = saveUserWithBalance(10_000);
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            Product socks = saveProduct("양말", 2_000, 10);
            Product hat = saveProduct("모자", 5_000, 10);
            Long orderId = placeOrder(
                user,
                new OrderItemCommand(shirt.getId(), 1),
                new OrderItemCommand(socks.getId(), 1),
                new OrderItemCommand(hat.getId(), 1)
            );
            adminProductFacade.delete(shirt.getId());
            adminProductFacade.delete(hat.getId());

            // act
            CoreException exception = rejected(() -> orderFacade.confirm(user.getId(), orderId));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_UNAVAILABLE);
            assertThat(exception.getMessage()).contains("(티셔츠, 모자)").doesNotContain("양말");
            assertUntouched(orderId, user, 10_000, Map.of(shirt, 10, socks, 10, hat, 10));
        }

        @DisplayName("품목 중 뒤쪽 재고가 부족하거나 0이 되었으면 OUT_OF_STOCK 이고, 앞 품목에서 이미 차감한 재고도 원래대로 돌아간다.")
        @ParameterizedTest
        @ValueSource(ints = {0, 1})
        void rollsBackEarlierStock_whenLaterItemIsOutOfStock(int remainingStock) {
            // arrange: 생성 때는 충분했지만, 확정 전에 관리자가 모자 재고를 줄였다.
            User user = saveUserWithBalance(10_000);
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            Product hat = saveProduct("모자", 5_000, 5);
            Long orderId = placeOrder(user, new OrderItemCommand(shirt.getId(), 2), new OrderItemCommand(hat.getId(), 2));
            adminProductFacade.changeStock(hat.getId(), remainingStock);

            // act
            CoreException exception = rejected(() -> orderFacade.confirm(user.getId(), orderId));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.OUT_OF_STOCK);
            assertUntouched(orderId, user, 10_000, Map.of(shirt, 10, hat, remainingStock));
        }

        @DisplayName("잔액이 부족하면 INSUFFICIENT_POINT 이고, 이미 차감했던 재고도 모두 원래대로 돌아가며 주문은 DRAFT 로 남는다.")
        @Test
        void rollsBackStock_whenBalanceIsInsufficient() {
            // arrange: 재고는 충분하지만 잔액 1,000 으로 합계 7,000 을 결제할 수 없다.
            User user = saveUserWithBalance(1_000);
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            Product socks = saveProduct("양말", 2_000, 10);
            Long orderId = placeOrder(user, new OrderItemCommand(shirt.getId(), 1), new OrderItemCommand(socks.getId(), 2));

            // act
            CoreException exception = rejected(() -> orderFacade.confirm(user.getId(), orderId));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INSUFFICIENT_POINT);
            assertUntouched(orderId, user, 1_000, Map.of(shirt, 10, socks, 10));
            assertThat(jdbcTemplate.queryForObject(
                "select paid_amount from orders where id = ?", Long.class, orderId
            )).isNull();
        }

        @DisplayName("오류 우선순위: 삭제된 상품이 있으면 재고 부족보다 PRODUCT_UNAVAILABLE 이 먼저이고, 재고 부족은 잔액 부족보다 먼저다.")
        @Test
        void followsErrorPriority() {
            // arrange: 잔액도 부족(1,000)하다.
            User user = saveUserWithBalance(1_000);
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            Product hat = saveProduct("모자", 5_000, 5);
            Long orderId = placeOrder(user, new OrderItemCommand(shirt.getId(), 2), new OrderItemCommand(hat.getId(), 2));
            adminProductFacade.changeStock(hat.getId(), 1);

            // act & assert: 재고 부족 + 잔액 부족 → OUT_OF_STOCK
            assertThat(rejected(() -> orderFacade.confirm(user.getId(), orderId)).getErrorType())
                .as("재고 부족 + 잔액 부족").isEqualTo(ErrorType.OUT_OF_STOCK);

            // 삭제된 상품 + 재고 부족 → PRODUCT_UNAVAILABLE
            adminProductFacade.delete(shirt.getId());
            assertThat(rejected(() -> orderFacade.confirm(user.getId(), orderId)).getErrorType())
                .as("삭제된 상품 + 재고 부족").isEqualTo(ErrorType.PRODUCT_UNAVAILABLE);
        }

        @DisplayName("요청자 식별 정보가 없으면(등록 누락) INTERNAL_ERROR 이고 아무것도 바꾸지 않는다.")
        @Test
        void throwsInternalError_whenRequesterIsMissing() {
            // arrange
            User user = saveUserWithBalance(10_000);
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            Long orderId = placeOrder(user, new OrderItemCommand(shirt.getId(), 1));

            // act
            CoreException exception = rejected(() -> orderFacade.confirm(null, orderId));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
            assertUntouched(orderId, user, 10_000, Map.of(shirt, 10));
        }
    }

    @DisplayName("내 주문을 조회할 때,")
    @Nested
    class Query {
        private User saveUserWithBalance(long amount) {
            User user = User.create();
            user.chargePoint(new Money(amount));
            return userRepository.save(user);
        }

        private Long placeOrder(User user, OrderItemCommand... items) {
            return orderFacade.create(user.getId(), List.of(items)).id();
        }

        @DisplayName("생성·확정한 주문의 응답에는 주문 시각이 담긴다.")
        @Test
        void includesOrderedAt_inCreateAndConfirmResult() {
            // arrange
            User user = saveUserWithBalance(10_000);
            Product shirt = saveProduct("티셔츠", 3_000, 10);

            // act
            OrderInfo created = orderFacade.create(user.getId(), List.of(new OrderItemCommand(shirt.getId(), 1)));
            OrderInfo confirmed = orderFacade.confirm(user.getId(), created.id());

            // assert
            assertThat(created.orderedAt()).isNotNull();
            assertThat(confirmed.orderedAt()).isEqualTo(created.orderedAt());
        }

        @DisplayName("목록은 내 주문만 최신순으로 요약(품목 수, 결제 결과, 주문 시각)해서 돌려준다.")
        @Test
        void returnsMyOrdersSummary_newestFirst() {
            // arrange
            User me = saveUserWithBalance(10_000);
            User other = saveUserWithBalance(10_000);
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            Product socks = saveProduct("양말", 2_000, 10);
            Long draftId = placeOrder(me, new OrderItemCommand(shirt.getId(), 1));
            placeOrder(other, new OrderItemCommand(shirt.getId(), 1));
            Long confirmedId = placeOrder(me, new OrderItemCommand(shirt.getId(), 1), new OrderItemCommand(socks.getId(), 2));
            orderFacade.confirm(me.getId(), confirmedId);

            // act
            Page<OrderSummaryInfo> page = orderFacade.getMyList(me.getId(), 0, 20);

            // assert
            assertThat(page.getContent()).extracting(OrderSummaryInfo::id).containsExactly(confirmedId, draftId);
            OrderSummaryInfo confirmed = page.getContent().get(0);
            assertThat(confirmed.userId()).isEqualTo(me.getId());
            assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(confirmed.totalAmount()).isEqualTo(7_000L);
            assertThat(confirmed.itemCount()).isEqualTo(2);
            assertThat(confirmed.payment()).isEqualTo(new PaymentInfo(7_000L, 3_000L));
            assertThat(confirmed.orderedAt()).isNotNull();
            OrderSummaryInfo draft = page.getContent().get(1);
            assertThat(draft.status()).isEqualTo(OrderStatus.DRAFT);
            assertThat(draft.itemCount()).isEqualTo(1);
            assertThat(draft.payment()).isNull();
        }

        @DisplayName("목록은 페이지 크기만큼 나누고, 주문이 없으면 빈 목록이다.")
        @Test
        void paginates_andReturnsEmpty_whenNoOrders() {
            // arrange
            User me = saveUserWithBalance(10_000);
            User nobody = saveUserWithBalance(10_000);
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            for (int i = 0; i < 3; i++) {
                placeOrder(me, new OrderItemCommand(shirt.getId(), 1));
            }

            // act
            Page<OrderSummaryInfo> firstPage = orderFacade.getMyList(me.getId(), 0, 2);
            Page<OrderSummaryInfo> empty = orderFacade.getMyList(nobody.getId(), 0, 20);

            // assert
            assertThat(firstPage.getContent()).hasSize(2);
            assertThat(firstPage.getTotalElements()).isEqualTo(3);
            assertThat(firstPage.getTotalPages()).isEqualTo(2);
            assertThat(empty.getContent()).isEmpty();
        }

        @DisplayName("잘못된 page·size 는 INVALID_INPUT 이다.")
        @Test
        void throwsInvalidInput_whenPageOrSizeIsOutOfRange() {
            User me = saveUserWithBalance(10_000);

            assertThat(rejected(() -> orderFacade.getMyList(me.getId(), -1, 20)).getErrorType())
                .as("page 음수").isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(rejected(() -> orderFacade.getMyList(me.getId(), 0, 101)).getErrorType())
                .as("size 초과").isEqualTo(ErrorType.INVALID_INPUT);
        }

        @DisplayName("상세는 품목·결제 결과·주문 시각을 돌려주고, 다른 사용자의 주문과 없는 주문은 NOT_FOUND 이다.")
        @Test
        void returnsMyOrderDetail_orThrowsNotFound() {
            // arrange
            User me = saveUserWithBalance(10_000);
            User other = saveUserWithBalance(10_000);
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            Long orderId = placeOrder(me, new OrderItemCommand(shirt.getId(), 2));
            orderFacade.confirm(me.getId(), orderId);

            // act
            OrderInfo info = orderFacade.getMine(me.getId(), orderId);
            CoreException otherUsers = rejected(() -> orderFacade.getMine(other.getId(), orderId));
            CoreException missing = rejected(() -> orderFacade.getMine(me.getId(), 999_999L));

            // assert
            assertThat(info.status()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(info.items()).containsExactly(new OrderItemInfo(shirt.getId(), "티셔츠", 2, 3_000L, 6_000L));
            assertThat(info.payment()).isEqualTo(new PaymentInfo(6_000L, 4_000L));
            assertThat(info.orderedAt()).isNotNull();
            assertThat(otherUsers.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(missing.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("요청자 식별 정보가 없으면(등록 누락) INTERNAL_ERROR 이다.")
        @Test
        void throwsInternalError_whenRequesterIsMissing() {
            assertThat(rejected(() -> orderFacade.getMyList(null, 0, 20)).getErrorType())
                .isEqualTo(ErrorType.INTERNAL_ERROR);
            assertThat(rejected(() -> orderFacade.getMine(null, 1L)).getErrorType())
                .isEqualTo(ErrorType.INTERNAL_ERROR);
        }
    }
}
