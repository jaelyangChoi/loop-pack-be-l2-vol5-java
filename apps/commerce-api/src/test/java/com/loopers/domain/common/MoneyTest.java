package com.loopers.domain.common;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @DisplayName("금액을 생성할 때,")
    @Nested
    class Create {
        @DisplayName("음수이면 INVALID_INPUT 으로 거절한다.")
        @Test
        void rejectsWithInvalidInput_whenAmountIsNegative() {
            CoreException exception = assertThrows(CoreException.class, () -> new Money(-1));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
        }

        @DisplayName("0이면 유효한 금액으로 생성된다.")
        @Test
        void createsMoney_whenAmountIsZero() {
            Money money = new Money(0);

            assertThat(money.amount()).isZero();
        }
    }

    @DisplayName("금액을 더할 때,")
    @Nested
    class Plus {
        @DisplayName("합계를 새 금액으로 반환하고, 기존 금액은 그대로다.")
        @Test
        void returnsSum_andKeepsOriginal() {
            Money money = new Money(10_000);

            Money sum = money.plus(new Money(3_000));

            assertThat(sum.amount()).isEqualTo(13_000);
            assertThat(money.amount()).isEqualTo(10_000);
        }

        @DisplayName("표현 범위를 넘으면 INVALID_INPUT 으로 거절한다.")
        @Test
        void rejectsWithInvalidInput_whenSumOverflows() {
            Money money = new Money(Long.MAX_VALUE);

            CoreException exception = assertThrows(CoreException.class, () -> money.plus(new Money(1)));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
        }
    }

    @DisplayName("금액을 뺄 때,")
    @Nested
    class Minus {
        @DisplayName("차액을 새 금액으로 반환하고, 기존 금액은 그대로다.")
        @Test
        void returnsDifference_andKeepsOriginal() {
            Money money = new Money(10_000);

            Money difference = money.minus(new Money(7_000));

            assertThat(difference.amount()).isEqualTo(3_000);
            assertThat(money.amount()).isEqualTo(10_000);
        }

        @DisplayName("같은 금액을 빼면 0이 된다.")
        @Test
        void returnsZero_whenAmountsAreEqual() {
            Money difference = new Money(10_000).minus(new Money(10_000));

            assertThat(difference.amount()).isZero();
        }

        @DisplayName("결과가 음수이면 INVALID_INPUT 으로 거절한다.")
        @Test
        void rejectsWithInvalidInput_whenResultIsNegative() {
            Money money = new Money(3_000);

            CoreException exception = assertThrows(CoreException.class, () -> money.minus(new Money(7_000)));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
        }
    }

    @DisplayName("금액에 수량을 곱할 때,")
    @Nested
    class Multiply {
        @DisplayName("곱한 금액을 새 금액으로 반환한다.")
        @Test
        void returnsProduct() {
            Money product = new Money(1_500).multiply(3);

            assertThat(product.amount()).isEqualTo(4_500);
        }

        @DisplayName("표현 범위를 넘으면 INVALID_INPUT 으로 거절한다.")
        @Test
        void rejectsWithInvalidInput_whenProductOverflows() {
            Money money = new Money(Long.MAX_VALUE);

            CoreException exception = assertThrows(CoreException.class, () -> money.multiply(2));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
        }

        @DisplayName("음수를 곱하면 INVALID_INPUT 으로 거절한다.")
        @Test
        void rejectsWithInvalidInput_whenMultiplierIsNegative() {
            Money money = new Money(1_500);

            CoreException exception = assertThrows(CoreException.class, () -> money.multiply(-1));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
        }
    }

    @DisplayName("금액이 0인지 확인할 때,")
    @Nested
    class IsZero {
        @DisplayName("0이면 true 를 반환한다.")
        @Test
        void returnsTrue_whenAmountIsZero() {
            assertThat(new Money(0).isZero()).isTrue();
        }

        @DisplayName("0이 아니면 false 를 반환한다.")
        @Test
        void returnsFalse_whenAmountIsNotZero() {
            assertThat(new Money(1).isZero()).isFalse();
        }
    }

    @DisplayName("금액을 비교할 때,")
    @Nested
    class IsLessThan {
        @DisplayName("더 작은 금액이면 true 를 반환한다.")
        @Test
        void returnsTrue_whenAmountIsLess() {
            assertThat(new Money(3_000).isLessThan(new Money(7_000))).isTrue();
        }

        @DisplayName("같거나 더 큰 금액이면 false 를 반환한다.")
        @Test
        void returnsFalse_whenAmountIsEqualOrGreater() {
            assertThat(new Money(7_000).isLessThan(new Money(7_000))).isFalse();
            assertThat(new Money(7_001).isLessThan(new Money(7_000))).isFalse();
        }
    }

    @DisplayName("금액이 같으면 같은 값으로 취급한다.")
    @Test
    void isEqual_whenAmountsAreSame() {
        assertThat(new Money(1_000)).isEqualTo(new Money(1_000));
    }
}
