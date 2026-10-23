package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    private static final long MAX_PRICE = 1_000_000_000L;

    private OrderProduct product(String name, long price) {
        return new OrderProduct(name, new Money(price));
    }

    private OrderLines lines(OrderLine... lines) {
        return new OrderLines(List.of(lines));
    }

    private void assertErrorType(ErrorType expected, Runnable action) {
        CoreException exception = assertThrows(CoreException.class, action::run);
        assertThat(exception.getErrorType()).isEqualTo(expected);
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class Create {
        @DisplayName("DRAFT 상태이고, 품목마다 주문 당시 상품명·단가와 금액(단가 × 수량)을 가지며, 합계는 품목 금액의 합이다.")
        @Test
        void createsDraft_withSnapshotItemsAndTotal() {
            // arrange: 4-1 시나리오. 3,000 × 1 + 2,000 × 2 = 7,000
            Map<Long, OrderProduct> products = Map.of(
                10L, product("티셔츠", 3_000),
                20L, product("양말", 2_000)
            );

            // act
            Order order = Order.create(7L, lines(new OrderLine(10L, 1), new OrderLine(20L, 2)), products);

            // assert
            assertThat(order.getUserId()).isEqualTo(7L);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
            assertThat(order.getTotalAmount()).isEqualTo(new Money(7_000));
            assertThat(order.getItems()).hasSize(2);

            OrderItem first = order.getItems().get(0);
            assertThat(first.getProductId()).isEqualTo(10L);
            assertThat(first.getProductName()).isEqualTo("티셔츠");
            assertThat(first.getQuantity()).isEqualTo(1);
            assertThat(first.getUnitPrice()).isEqualTo(new Money(3_000));
            assertThat(first.getAmount()).isEqualTo(new Money(3_000));

            OrderItem second = order.getItems().get(1);
            assertThat(second.getProductId()).isEqualTo(20L);
            assertThat(second.getProductName()).isEqualTo("양말");
            assertThat(second.getQuantity()).isEqualTo(2);
            assertThat(second.getUnitPrice()).isEqualTo(new Money(2_000));
            assertThat(second.getAmount()).isEqualTo(new Money(4_000));
        }

        @DisplayName("품목의 상품 id 를 품목 순서대로 돌려준다.")
        @Test
        void returnsProductIds_inItemOrder() {
            // arrange
            Order order = Order.create(
                7L,
                lines(new OrderLine(20L, 1), new OrderLine(10L, 2), new OrderLine(30L, 1)),
                Map.of(10L, product("티셔츠", 3_000), 20L, product("양말", 2_000), 30L, product("모자", 5_000))
            );

            // act & assert
            assertThat(order.productIds()).containsExactly(20L, 10L, 30L);
        }

        @DisplayName("요청자가 없으면 INVALID_INPUT 으로 거절한다.")
        @Test
        void throwsInvalidInput_whenUserIdIsNull() {
            assertErrorType(ErrorType.INVALID_INPUT, () ->
                Order.create(null, lines(new OrderLine(10L, 1)), Map.of(10L, product("티셔츠", 3_000))));
        }

        @DisplayName("품목에 해당하는 상품이 주어지지 않으면 호출 오류이므로 INTERNAL_ERROR 로 거절한다.")
        @Test
        void throwsInternalError_whenProductIsMissing() {
            assertErrorType(ErrorType.INTERNAL_ERROR, () ->
                Order.create(7L, lines(new OrderLine(10L, 1), new OrderLine(20L, 1)), Map.of(10L, product("티셔츠", 3_000))));
        }

        @DisplayName("합계가 금액 표현 범위를 넘으면 INVALID_INPUT 으로 거절한다.")
        @Test
        void throwsInvalidInput_whenTotalOverflows() {
            // arrange: 품목 하나는 최대 단가 × Integer.MAX_VALUE ≈ 2.1e18 로 long 범위 안이지만, 다섯 개를 더하면 9.2e18 을 넘는다.
            Map<Long, OrderProduct> products = Map.of(
                1L, product("가", MAX_PRICE),
                2L, product("나", MAX_PRICE),
                3L, product("다", MAX_PRICE),
                4L, product("라", MAX_PRICE),
                5L, product("마", MAX_PRICE)
            );
            OrderLines lines = lines(
                new OrderLine(1L, Integer.MAX_VALUE),
                new OrderLine(2L, Integer.MAX_VALUE),
                new OrderLine(3L, Integer.MAX_VALUE),
                new OrderLine(4L, Integer.MAX_VALUE),
                new OrderLine(5L, Integer.MAX_VALUE)
            );

            // act & assert
            assertErrorType(ErrorType.INVALID_INPUT, () -> Order.create(7L, lines, products));
        }
    }

    @DisplayName("주문을 확정할 때,")
    @Nested
    class Confirm {
        private Order draftOrder() {
            return Order.create(
                7L,
                lines(new OrderLine(10L, 1), new OrderLine(20L, 2)),
                Map.of(10L, product("티셔츠", 3_000), 20L, product("양말", 2_000))
            );
        }

        // 합계 7,000원을 결제한 결과
        private PaymentResult payment() {
            return new PaymentResult(new Money(7_000));
        }

        @DisplayName("DRAFT 주문은 확정할 수 있는 상태다.")
        @Test
        void isConfirmable_whenDraft() {
            // act & assert: 예외가 나지 않는다.
            draftOrder().ensureConfirmable();
        }

        @DisplayName("확정하면 CONFIRMED 가 되고, 결제액을 보존한다. 품목과 합계는 그대로다.")
        @Test
        void becomesConfirmed_withPaymentResult() {
            // arrange
            Order order = draftOrder();

            // act
            order.confirm(payment());

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.getPaymentResult()).isEqualTo(payment());
            assertThat(order.getTotalAmount()).isEqualTo(new Money(7_000));
            assertThat(order.getItems()).hasSize(2);
        }

        @DisplayName("DRAFT 주문은 결제 결과가 비어 있다.")
        @Test
        void hasNoPaymentResult_whenDraft() {
            assertThat(draftOrder().getPaymentResult()).isNull();
        }

        @DisplayName("이미 확정된 주문은 ORDER_ALREADY_CONFIRMED 로 거절하고, 기존 결제 결과를 유지한다.")
        @Test
        void rejectsWithAlreadyConfirmed_andKeepsPaymentResult_whenAlreadyConfirmed() {
            // arrange
            Order order = draftOrder();
            order.confirm(payment());
            PaymentResult other = new PaymentResult(new Money(1));

            // act & assert
            assertErrorType(ErrorType.ORDER_ALREADY_CONFIRMED, order::ensureConfirmable);
            assertErrorType(ErrorType.ORDER_ALREADY_CONFIRMED, () -> order.confirm(other));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.getPaymentResult()).isEqualTo(payment());
        }

        @DisplayName("결제 결과가 없으면 INVALID_INPUT 으로 거절하고, DRAFT 를 유지한다.")
        @Test
        void rejectsWithInvalidInput_andStaysDraft_whenPaymentResultIsNull() {
            // arrange
            Order order = draftOrder();

            // act & assert
            assertErrorType(ErrorType.INVALID_INPUT, () -> order.confirm(null));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
            assertThat(order.getPaymentResult()).isNull();
        }
    }
}
