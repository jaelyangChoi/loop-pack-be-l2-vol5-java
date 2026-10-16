package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderLineTest {

    private void assertInvalidInput(Runnable action) {
        CoreException exception = assertThrows(CoreException.class, action::run);
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
    }

    @DisplayName("주문 요청 한 줄을 만들 때,")
    @Nested
    class Create {
        @DisplayName("상품 id 와 1 이상의 수량이면 값을 그대로 가진다.")
        @Test
        void keepsValues_whenQuantityIsPositive() {
            // act
            OrderLine line = new OrderLine(3L, 2);

            // assert
            assertThat(line.productId()).isEqualTo(3L);
            assertThat(line.quantity()).isEqualTo(2);
        }

        @DisplayName("수량이 0 이하이면 INVALID_INPUT 으로 거절한다.")
        @ParameterizedTest
        @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
        void throwsInvalidInput_whenQuantityIsNotPositive(int quantity) {
            assertInvalidInput(() -> new OrderLine(3L, quantity));
        }

        @DisplayName("상품 id 가 없으면 INVALID_INPUT 으로 거절한다.")
        @Test
        void throwsInvalidInput_whenProductIdIsNull() {
            assertInvalidInput(() -> new OrderLine(null, 1));
        }
    }
}
