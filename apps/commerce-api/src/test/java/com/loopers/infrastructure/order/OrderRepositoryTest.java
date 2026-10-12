package com.loopers.infrastructure.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderLines;
import com.loopers.domain.order.OrderProduct;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    // 4-1 시나리오: 3,000 × 1 + 2,000 × 2 = 7,000
    private Order newOrder(Long userId) {
        return Order.create(
            userId,
            new OrderLines(List.of(new OrderLine(10L, 1), new OrderLine(20L, 2))),
            Map.of(
                10L, new OrderProduct("티셔츠", new Money(3_000)),
                20L, new OrderProduct("양말", new Money(2_000))
            )
        );
    }

    @DisplayName("주문을 저장하고 다시 읽을 때,")
    @Nested
    class SaveAndFind {
        @DisplayName("상태·합계·품목(상품명·수량·단가·금액)이 DB 에서 그대로 읽히고, 시각이 채워진다.")
        @Test
        void returnsSavedOrderWithItems() {
            // arrange
            Long orderId = orderRepository.save(newOrder(7L)).getId();

            // act: 같은 메모리 객체가 아니라 DB 에서 다시 읽는다. 품목은 지연 로딩이라 트랜잭션 안에서 읽는다.
            transactionTemplate.executeWithoutResult(status -> {
                Order found = orderJpaRepository.findById(orderId).orElseThrow();

                // assert
                assertThat(found.getUserId()).isEqualTo(7L);
                assertThat(found.getStatus()).isEqualTo(OrderStatus.DRAFT);
                assertThat(found.getTotalAmount()).isEqualTo(new Money(7_000));
                assertThat(found.getCreatedAt()).isNotNull();
                assertThat(found.getItems()).hasSize(2);

                OrderItem first = found.getItems().get(0);
                assertThat(first.getProductId()).isEqualTo(10L);
                assertThat(first.getProductName()).isEqualTo("티셔츠");
                assertThat(first.getQuantity()).isEqualTo(1);
                assertThat(first.getUnitPrice()).isEqualTo(new Money(3_000));
                assertThat(first.getAmount()).isEqualTo(new Money(3_000));

                OrderItem second = found.getItems().get(1);
                assertThat(second.getProductId()).isEqualTo(20L);
                assertThat(second.getProductName()).isEqualTo("양말");
                assertThat(second.getQuantity()).isEqualTo(2);
                assertThat(second.getUnitPrice()).isEqualTo(new Money(2_000));
                assertThat(second.getAmount()).isEqualTo(new Money(4_000));
            });
        }

        @DisplayName("품목은 주문과 함께 저장되어 order_items 에 주문 id 로 연결된다.")
        @Test
        void savesItemsTogetherWithOrder() {
            // act
            Long orderId = orderRepository.save(newOrder(7L)).getId();

            // assert
            Integer count = jdbcTemplate.queryForObject(
                "select count(*) from order_items where order_id = ?", Integer.class, orderId
            );
            assertThat(count).isEqualTo(2);
        }
    }
}
