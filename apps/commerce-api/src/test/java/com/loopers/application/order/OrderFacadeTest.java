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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
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
            // arrange: 품목 하나는 범위 안이지만 다섯 개를 더하면 long 범위를 넘는다.
            User user = saveUser();
            List<OrderItemCommand> items = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Product product = saveProduct("상품" + i, MAX_PRICE, 1);
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
}
