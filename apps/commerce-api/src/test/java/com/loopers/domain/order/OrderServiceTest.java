package com.loopers.domain.order;

import com.loopers.domain.common.Money;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class Create {
        @DisplayName("DRAFT 주문을 저장해 id 와 함께 돌려준다.")
        @Test
        void savesDraftOrder() {
            // act
            Order order = orderService.create(
                7L,
                new OrderLines(List.of(new OrderLine(10L, 2))),
                Map.of(10L, new OrderProduct("티셔츠", new Money(3_000)))
            );

            // assert
            assertThat(order.getId()).isNotNull();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
            assertThat(order.getTotalAmount()).isEqualTo(new Money(6_000));
            assertThat(jdbcTemplate.queryForObject("select count(*) from orders", Integer.class)).isEqualTo(1);
        }

        @DisplayName("도메인 규칙에 어긋나면 예외가 나고 아무것도 저장하지 않는다.")
        @Test
        void savesNothing_whenOrderIsInvalid() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> orderService.create(
                null,
                new OrderLines(List.of(new OrderLine(10L, 2))),
                Map.of(10L, new OrderProduct("티셔츠", new Money(3_000)))
            ));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(jdbcTemplate.queryForObject("select count(*) from orders", Integer.class)).isZero();
        }
    }
}
