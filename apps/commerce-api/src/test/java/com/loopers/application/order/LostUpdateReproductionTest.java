package com.loopers.application.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 갱신 유실이 왜 생기는지 보여 주는 <b>대조군</b>이다. 제품 구현이 아니며, 이 테스트의 통과는 실제 주문의 정합성 증거가 아니다.
 * 실제 서비스의 동시성 검증은 {@code OrderConcurrencyTest} 가 한다.
 *
 * <p>commit 된 재고 5 를 독립된 트랜잭션 두 개가 잠금 없는 SELECT 로 읽는다. 두 값이 모두 5 임을 확인한 뒤에야 쓰기를
 * 허용하고, 각자 읽은 값에서 1 을 뺀 <b>상수 4</b> 를 조건·version 없이 저장한다. InnoDB 의 REPEATABLE READ 는 이
 * 덮어쓰기를 감지해 거절하지 않으므로 둘 다 성공하고 재고는 4 가 된다. 읽기 뒤 장벽은 이 대조군 안에만 있으며,
 * {@code stock = stock - 1} 같은 SQL 은 이 예제와 달라 대조군으로 쓰지 않는다.
 */
@SpringBootTest
class LostUpdateReproductionTest {

    private static final long TIMEOUT_SECONDS = 10;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("잠금 없이 읽은 재고 5 에서 각자 1 을 빼 저장하면, 둘 다 성공하지만 재고가 4 가 되어 하나가 유실된다.")
    @Test
    void losesOneUpdate_whenTwoTransactionsWriteConstantFromStaleRead() throws Exception {
        // arrange: 데이터를 commit 해 둔다.
        Product product = productRepository.save(Product.create(1L, "러닝화", new Money(89_000), new Stock(5)));
        int workers = 2;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch bothRead = new CountDownLatch(workers);
        try {
            // act
            List<Future<Integer>> readValues = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                readValues.add(executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    int stock = jdbcTemplate.queryForObject(
                        "select stock from products where id = ?", Integer.class, product.getId());
                    bothRead.countDown();
                    awaitBothRead(bothRead);
                    // 각자 읽은 값에서 1 을 뺀 상수를 조건 없이 저장한다. 두 번째 UPDATE 는 첫 트랜잭션의 commit 을 기다린 뒤 덮어쓴다.
                    jdbcTemplate.update("update products set stock = ? where id = ?", stock - 1, product.getId());
                    return stock;
                })));
            }

            // assert: 두 트랜잭션이 읽은 값은 모두 5, 둘 다 성공했는데 재고는 4 다.
            for (Future<Integer> readValue : readValues) {
                assertThat(readValue.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(5);
            }
            int finalStock = jdbcTemplate.queryForObject(
                "select stock from products where id = ?", Integer.class, product.getId());
            assertThat(finalStock).isEqualTo(4);
            // 불변식 초기 재고 − 성공한 차감 = 최종 재고 가 깨졌다: 5 − 2 = 3 이어야 하는데 4 다.
            assertThat(5 - workers).isNotEqualTo(finalStock);
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitBothRead(CountDownLatch bothRead) {
        try {
            if (!bothRead.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("두 트랜잭션이 제한 시간 안에 모두 읽지 못했다. 재현으로 세지 않는다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("대기 중 중단되었다.", e);
        }
    }
}
