package com.loopers.application.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.jpa.FailingStatementInspector;
import com.loopers.support.jpa.FailingStatementInspector.InjectedFailureException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 주문 확정의 재고 차감·포인트 결제·상태 변경·결제 결과 저장이 함께 commit 되거나 함께 취소되는지 실제 MySQL 로 확인한다.
 * 확정이 전역 잠금 순서(주문 → 상품 → 사용자, D-27)대로 행을 잠그는지도 실행된 SQL 로 확인한다.
 *
 * <p>테스트를 부모 트랜잭션으로 감싸지 않는다. 데이터는 각각 commit 해 두고, 확정 뒤의 상태는 영속성 컨텍스트를 거치지
 * 않는 {@link JdbcTemplate} 으로 새로 읽는다. 실패는 {@link FailingStatementInspector} 로 사용자 UPDATE 문이 DB 로
 * 나가기 직전에 주입하며, 운영 코드에는 실패용 스위치가 없다.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
    + "com.loopers.support.jpa.FailingStatementInspector")
class OrderTransactionTest {

    private static final Pattern FROM_TABLE = Pattern.compile("\\bfrom (\\w+)");

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        FailingStatementInspector.disarm();
        databaseCleanUp.truncateAllTables();
    }

    private User saveUserWithBalance(long amount) {
        User user = User.create();
        user.chargePoint(new Money(amount));
        return userRepository.save(user);
    }

    private Product saveProduct(String name, long price, int stock) {
        return productRepository.save(Product.create(1L, name, new Money(price), new Stock(stock)));
    }

    private int stockOf(Product product) {
        return jdbcTemplate.queryForObject("select stock from products where id = ?", Integer.class, product.getId());
    }

    private long balanceOf(User user) {
        return jdbcTemplate.queryForObject("select point_balance from users where id = ?", Long.class, user.getId());
    }

    private String statusOf(Long orderId) {
        return jdbcTemplate.queryForObject("select status from orders where id = ?", String.class, orderId);
    }

    private Map<String, Object> paymentOf(Long orderId) {
        return jdbcTemplate.queryForMap("select paid_amount, balance_after from orders where id = ?", orderId);
    }

    // `for update` 로 끝나는 조회가 어떤 테이블을 어떤 순서로 잠갔는지 돌려준다.
    private List<String> lockedTables() {
        return FailingStatementInspector.statements().stream()
            .filter(sql -> sql.endsWith("for update"))
            .map(sql -> {
                Matcher matcher = FROM_TABLE.matcher(sql);
                assertThat(matcher.find()).as(sql).isTrue();
                return matcher.group(1);
            })
            .toList();
    }

    @DisplayName("주문을 확정할 때,")
    @Nested
    class Confirm {
        @DisplayName("재고 차감, 포인트 결제, CONFIRMED, 결제 결과가 함께 commit 된다.")
        @Test
        void commitsStockPointStatusAndPayment_together() {
            // arrange: 잔액 10,000 → 3,000×1 + 2,000×2 = 7,000 결제 → 잔액 3,000
            User user = saveUserWithBalance(10_000);
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            Product socks = saveProduct("양말", 2_000, 5);
            Long orderId = orderFacade.create(
                user.getId(),
                List.of(new OrderItemCommand(shirt.getId(), 1), new OrderItemCommand(socks.getId(), 2))
            ).id();

            // act
            orderFacade.confirm(user.getId(), orderId);

            // assert
            assertThat(statusOf(orderId)).isEqualTo("CONFIRMED");
            assertThat(stockOf(shirt)).isEqualTo(9);
            assertThat(stockOf(socks)).isEqualTo(3);
            assertThat(balanceOf(user)).isEqualTo(3_000);
            assertThat(paymentOf(orderId)).containsEntry("paid_amount", 7_000L).containsEntry("balance_after", 3_000L);
        }

        @DisplayName("주문 → 상품 → 사용자 순서로 행을 잠근다.")
        @Test
        void locksOrderThenProductsThenUser() {
            // arrange
            User user = saveUserWithBalance(10_000);
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            Product socks = saveProduct("양말", 2_000, 5);
            Long orderId = orderFacade.create(
                user.getId(),
                List.of(new OrderItemCommand(socks.getId(), 2), new OrderItemCommand(shirt.getId(), 1))
            ).id();
            FailingStatementInspector.record();

            // act
            orderFacade.confirm(user.getId(), orderId);

            // assert: 상품은 품목 순서와 무관하게 한 번의 조회로 id 오름차순 잠금(조회 SQL 에 order by id)을 건다.
            assertThat(lockedTables()).containsExactly("orders", "products", "users");
            assertThat(FailingStatementInspector.statements())
                .anyMatch(sql -> sql.contains("from products") && sql.endsWith("order by p1_0.id for update"));
        }

        @DisplayName("재고 차감이 DB 로 전송된 뒤 다음 단계가 실패하면 재고·잔액·상태·결제 결과가 모두 변경 전으로 돌아간다.")
        @Test
        void rollsBackEverything_whenPaymentFailsAfterStockUpdateSent() {
            // arrange
            User user = saveUserWithBalance(10_000);
            Product shirt = saveProduct("티셔츠", 3_000, 10);
            Product socks = saveProduct("양말", 2_000, 5);
            Long orderId = orderFacade.create(
                user.getId(),
                List.of(new OrderItemCommand(shirt.getId(), 1), new OrderItemCommand(socks.getId(), 2))
            ).id();
            FailingStatementInspector.arm("update users", 1);

            // act & assert: 실패는 밖으로 전달된다. Spring 이 감쌀 수 있으므로 원인 사슬에서 주입한 예외를 찾는다.
            assertThatThrownBy(() -> orderFacade.confirm(user.getId(), orderId))
                .hasRootCauseInstanceOf(InjectedFailureException.class);

            // 실패 전에 재고 차감 UPDATE 가 실제로 DB 로 나갔어야 rollback 검증이 의미를 가진다.
            List<String> mutations = FailingStatementInspector.mutations();
            assertThat(mutations.get(mutations.size() - 1)).startsWith("update users");
            assertThat(mutations.subList(0, mutations.size() - 1)).anyMatch(sql -> sql.startsWith("update products"));

            FailingStatementInspector.disarm();
            assertThat(statusOf(orderId)).isEqualTo("DRAFT");
            assertThat(stockOf(shirt)).isEqualTo(10);
            assertThat(stockOf(socks)).isEqualTo(5);
            assertThat(balanceOf(user)).isEqualTo(10_000);
            assertThat(paymentOf(orderId)).containsEntry("paid_amount", null).containsEntry("balance_after", null);
        }
    }
}
