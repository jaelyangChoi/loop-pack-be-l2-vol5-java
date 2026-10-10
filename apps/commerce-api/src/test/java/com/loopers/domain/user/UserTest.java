package com.loopers.domain.user;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTest {

    @DisplayName("사용자를 생성할 때,")
    @Nested
    class Create {
        @DisplayName("포인트 잔액은 0으로 시작한다.")
        @Test
        void startsWithZeroBalance() {
            // act
            User user = new User();

            // assert
            assertThat(user.getPointBalance()).isEqualTo(new PointBalance(new Money(0)));
        }
    }

    @DisplayName("포인트를 충전할 때,")
    @Nested
    class ChargePoint {
        @DisplayName("충전액이 잔액에 반영된다.")
        @Test
        void increasesBalance_whenAmountIsValid() {
            // arrange
            User user = new User();

            // act
            user.chargePoint(new Money(10_000));

            // assert
            assertThat(user.getPointBalance()).isEqualTo(new PointBalance(new Money(10_000)));
        }

        @DisplayName("충전액이 0이면 INVALID_INPUT 으로 거절하고, 기존 잔액은 그대로다.")
        @Test
        void rejectsWithInvalidInput_andKeepsBalance_whenAmountIsZero() {
            // arrange
            User user = new User();
            user.chargePoint(new Money(5_000));

            // act
            CoreException exception = assertThrows(CoreException.class, () -> user.chargePoint(new Money(0)));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(user.getPointBalance()).isEqualTo(new PointBalance(new Money(5_000)));
        }
    }

    @DisplayName("포인트로 결제할 때,")
    @Nested
    class PayPoint {
        @DisplayName("결제액이 잔액에서 차감된다.")
        @Test
        void decreasesBalance_whenAmountIsValid() {
            // arrange
            User user = new User();
            user.chargePoint(new Money(10_000));

            // act
            user.payPoint(new Money(7_000));

            // assert
            assertThat(user.getPointBalance()).isEqualTo(new PointBalance(new Money(3_000)));
        }

        @DisplayName("결제액이 잔액보다 크면 INSUFFICIENT_POINT 로 거절하고, 기존 잔액은 그대로다.")
        @Test
        void rejectsWithInsufficientPoint_andKeepsBalance_whenAmountIsGreaterThanBalance() {
            // arrange
            User user = new User();
            user.chargePoint(new Money(3_000));

            // act
            CoreException exception = assertThrows(CoreException.class, () -> user.payPoint(new Money(7_000)));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INSUFFICIENT_POINT);
            assertThat(user.getPointBalance()).isEqualTo(new PointBalance(new Money(3_000)));
        }
    }
}
