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

    }

    @DisplayName("본인 주문을 조회할 때,")
    @Nested
    class GetMine {
        private Order saveOrder(Long userId) {
            return orderService.create(
                userId,
                new OrderLines(List.of(new OrderLine(10L, 2))),
                Map.of(10L, new OrderProduct("티셔츠", new Money(3_000)))
            );
        }

        @DisplayName("주문자가 같으면 주문을 돌려준다.")
        @Test
        void returnsOrder_whenUserIsOwner() {
            // arrange
            Order saved = saveOrder(7L);

            // act
            Order found = orderService.getMine(saved.getId(), 7L);

            // assert
            assertThat(found.getId()).isEqualTo(saved.getId());
        }

        @DisplayName("다른 사용자의 주문과 없는 주문은 구분하지 않고 NOT_FOUND 로 거절한다.")
        @Test
        void throwsNotFound_whenOrderBelongsToOtherUserOrDoesNotExist() {
            // arrange
            Order saved = saveOrder(7L);

            // act
            CoreException otherUser = assertThrows(CoreException.class, () -> orderService.getMine(saved.getId(), 8L));
            CoreException missing = assertThrows(CoreException.class, () -> orderService.getMine(999_999L, 7L));

            // assert
            assertThat(otherUser.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(missing.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(otherUser.getMessage()).isEqualTo(missing.getMessage());
        }
    }

    @DisplayName("주문 하나를 조회할 때,")
    @Nested
    class Get {
        @DisplayName("있으면 누구의 주문이든 돌려주고, 없으면 NOT_FOUND 로 거절한다.")
        @Test
        void returnsOrder_orThrowsNotFound() {
            // arrange
            Order saved = orderService.create(
                7L,
                new OrderLines(List.of(new OrderLine(10L, 2))),
                Map.of(10L, new OrderProduct("티셔츠", new Money(3_000)))
            );

            // act
            Order found = orderService.get(saved.getId());
            CoreException exception = assertThrows(CoreException.class, () -> orderService.get(999_999L));

            // assert
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(exception.getMessage()).isEqualTo("주문을 찾을 수 없습니다.");
        }
    }
}
