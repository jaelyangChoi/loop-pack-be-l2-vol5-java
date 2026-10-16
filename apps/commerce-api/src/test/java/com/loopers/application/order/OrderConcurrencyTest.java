package com.loopers.application.order;

import com.loopers.application.point.PointFacade;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.concurrent.ConcurrentRequests;
import com.loopers.support.concurrent.ConcurrentRequests.Results;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 재고·잔액·주문을 향한 동시 요청이 실제 Facade·Repository·MySQL 을 지나며 서로의 결과를 유실시키지 않는지 확인한다
 * (D-25, D-26). 갱신 유실이 왜 생기는지는 대조군 {@code LostUpdateReproductionTest} 가 따로 보여 준다.
 *
 * <p>데이터는 요청을 시작하기 전에 commit 해 두고 테스트를 부모 트랜잭션으로 감싸지 않는다. 요청은 출발만 맞춰 동시에
 * 시작하며 잠금 구간에는 장벽이나 sleep 이 없다. 요청 결과를 성공 / 업무 거절 / 기술 오류로 모으고, 모든 요청이 끝난 뒤
 * {@link JdbcTemplate} 으로 DB 를 새로 읽어 집계식과 맞춰 본다. 최종 재고나 잔액이 음수가 아니라는 확인만으로는 모든
 * 요청을 거절한 구현도 통과하므로, 성공 건수와 거절 건수까지 함께 확인한다. 경쟁은 확률적이므로 반복 실행한다.
 */
@SpringBootTest
class OrderConcurrencyTest {

    private static final int REPETITIONS = 10;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private PointFacade pointFacade;

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
        databaseCleanUp.truncateAllTables();
    }

    private User saveUserWithBalance(long amount) {
        User user = User.create();
        user.chargePoint(new Money(amount));
        return userRepository.save(user);
    }

    private Product saveProduct(long price, int stock) {
        return productRepository.save(Product.create(1L, "러닝화", new Money(price), new Stock(stock)));
    }

    private Long placeOrder(User user, Product product, int quantity) {
        return orderFacade.create(user.getId(), List.of(new OrderItemCommand(product.getId(), quantity))).id();
    }

    private Runnable confirm(User user, Long orderId) {
        return () -> orderFacade.confirm(user.getId(), orderId);
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

    private long confirmedOrderCount() {
        return jdbcTemplate.queryForObject("select count(*) from orders where status = 'CONFIRMED'", Long.class);
    }

    // CONFIRMED 주문 품목의 수량 합. 초기 재고 − 이 값 = 최종 재고 여야 한다.
    private long confirmedQuantity() {
        return jdbcTemplate.queryForObject(
            "select coalesce(sum(i.quantity), 0) from order_items i join orders o on o.id = i.order_id "
                + "where o.status = 'CONFIRMED'", Long.class);
    }

    // CONFIRMED 주문의 결제액 합. 초기 잔액 + 충전액 − 이 값 = 최종 잔액 이어야 한다.
    private long confirmedPaidAmount() {
        return jdbcTemplate.queryForObject(
            "select coalesce(sum(paid_amount), 0) from orders where status = 'CONFIRMED'", Long.class);
    }

    private void assertAccountedFor(Results results, int requests) {
        assertThat(results.successes() + results.rejections() + results.technicalErrors())
            .as("성공 + 업무 거절 + 기술 오류 = 전체 요청: %s", results.outcomes())
            .isEqualTo(requests);
        assertThat(results.technicalErrors()).as("기술 오류: %s", results.outcomes()).isZero();
    }

    @DisplayName("재고 5 에 서로 다른 주문 8개를 동시에 확정하면, 5개만 확정되고 3개는 재고 부족으로 거절되며 최종 재고는 0 이다.")
    @RepeatedTest(REPETITIONS)
    void confirmsOnlyAsManyOrdersAsStock_whenConfirmingConcurrently() {
        // arrange: 구매자별로 충분한 잔액을 둔다. 재고 부족이 아니면 거절될 이유가 없다.
        int initialStock = 5;
        int requests = 8;
        Product product = saveProduct(1_000, initialStock);
        List<User> buyers = new ArrayList<>();
        List<Long> orderIds = new ArrayList<>();
        List<Runnable> confirmations = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            User buyer = saveUserWithBalance(10_000);
            Long orderId = placeOrder(buyer, product, 1);
            buyers.add(buyer);
            orderIds.add(orderId);
            confirmations.add(confirm(buyer, orderId));
        }

        // act
        Results results = ConcurrentRequests.run(confirmations);

        // assert
        assertAccountedFor(results, requests);
        assertThat(results.successes()).isEqualTo(5);
        assertThat(results.rejectedWith(ErrorType.OUT_OF_STOCK)).isEqualTo(3);
        assertThat(stockOf(product)).isZero();
        assertThat(initialStock - confirmedQuantity()).isEqualTo(stockOf(product));
        assertThat(confirmedOrderCount()).isEqualTo(5);
        // 확정된 주문의 구매자만 결제액이 빠지고, 거절된 주문은 DRAFT 이며 구매자의 잔액이 그대로다.
        long confirmedBuyers = 0;
        for (int i = 0; i < requests; i++) {
            if (statusOf(orderIds.get(i)).equals("CONFIRMED")) {
                assertThat(balanceOf(buyers.get(i))).isEqualTo(9_000);
                confirmedBuyers++;
            } else {
                assertThat(statusOf(orderIds.get(i))).isEqualTo("DRAFT");
                assertThat(balanceOf(buyers.get(i))).isEqualTo(10_000);
            }
        }
        assertThat(confirmedBuyers).isEqualTo(5);
    }

    @DisplayName("잔액 10,000 에 4,000 원 주문 3개를 동시에 확정하면, 2개만 확정되고 1개는 잔액 부족으로 거절되며 최종 잔액은 2,000 이다.")
    @RepeatedTest(REPETITIONS)
    void confirmsOnlyAsManyOrdersAsBalance_whenConfirmingConcurrently() {
        // arrange: 재고는 충분히 둬서 잔액 보호의 실패를 재고 부족이 가리지 않게 한다.
        int initialStock = 100;
        long initialBalance = 10_000;
        int requests = 3;
        Product product = saveProduct(4_000, initialStock);
        User buyer = saveUserWithBalance(initialBalance);
        List<Long> orderIds = new ArrayList<>();
        List<Runnable> confirmations = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            Long orderId = placeOrder(buyer, product, 1);
            orderIds.add(orderId);
            confirmations.add(confirm(buyer, orderId));
        }

        // act
        Results results = ConcurrentRequests.run(confirmations);

        // assert
        assertAccountedFor(results, requests);
        assertThat(results.successes()).isEqualTo(2);
        assertThat(results.rejectedWith(ErrorType.INSUFFICIENT_POINT)).isEqualTo(1);
        assertThat(balanceOf(buyer)).isEqualTo(2_000);
        assertThat(initialBalance - confirmedPaidAmount()).isEqualTo(balanceOf(buyer));
        // 거절된 주문은 재고도 차감하지 않는다.
        assertThat(stockOf(product)).isEqualTo(initialStock - 2);
        assertThat(initialStock - confirmedQuantity()).isEqualTo(stockOf(product));
        assertThat(orderIds.stream().filter(orderId -> statusOf(orderId).equals("DRAFT"))).hasSize(1);
    }

    @DisplayName("잔액 10,000 에서 2,000 충전과 7,000 확정을 동시에 하면, 둘 다 성공하고 최종 잔액은 5,000 이다.")
    @RepeatedTest(REPETITIONS)
    void keepsBothChargeAndPayment_whenRunningConcurrently() {
        // arrange
        long initialBalance = 10_000;
        long chargeAmount = 2_000;
        Product product = saveProduct(7_000, 10);
        User buyer = saveUserWithBalance(initialBalance);
        Long orderId = placeOrder(buyer, product, 1);

        // act
        Results results = ConcurrentRequests.run(List.of(
            confirm(buyer, orderId),
            () -> pointFacade.charge(buyer.getId(), chargeAmount)
        ));

        // assert: 순서와 무관하게 어느 쪽도 유실되지 않는다.
        assertAccountedFor(results, 2);
        assertThat(results.successes()).isEqualTo(2);
        assertThat(balanceOf(buyer)).isEqualTo(5_000);
        assertThat(initialBalance + chargeAmount - confirmedPaidAmount()).isEqualTo(balanceOf(buyer));
        assertThat(statusOf(orderId)).isEqualTo("CONFIRMED");
    }

    @DisplayName("같은 주문을 동시에 5번 확정하면, 한 번만 확정되고 나머지는 이미 확정된 주문으로 거절되며 재고와 잔액은 한 번만 차감된다.")
    @RepeatedTest(REPETITIONS)
    void deductsOnlyOnce_whenConfirmingSameOrderConcurrently() {
        // arrange
        int requests = 5;
        Product product = saveProduct(3_000, 10);
        User buyer = saveUserWithBalance(10_000);
        Long orderId = placeOrder(buyer, product, 2);
        List<Runnable> confirmations = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            confirmations.add(confirm(buyer, orderId));
        }

        // act
        Results results = ConcurrentRequests.run(confirmations);

        // assert
        assertAccountedFor(results, requests);
        assertThat(results.successes()).isEqualTo(1);
        assertThat(results.rejectedWith(ErrorType.ORDER_ALREADY_CONFIRMED)).isEqualTo(requests - 1);
        assertThat(statusOf(orderId)).isEqualTo("CONFIRMED");
        assertThat(stockOf(product)).isEqualTo(8);
        assertThat(balanceOf(buyer)).isEqualTo(4_000);
    }
}
