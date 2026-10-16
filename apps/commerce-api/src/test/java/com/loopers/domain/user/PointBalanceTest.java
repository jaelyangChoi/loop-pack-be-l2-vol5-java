package com.loopers.domain.user;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PointBalanceTest {

    @DisplayName("포인트를 충전할 때,")
    @Nested
    class Charge {
        @DisplayName("충전액을 더한 새 잔액을 반환하고, 기존 잔액은 그대로다.")
        @Test
        void returnsChargedBalance_andKeepsOriginal() {
            // arrange
            PointBalance balance = new PointBalance(new Money(0));

            // act
            PointBalance charged = balance.charge(new Money(10_000));

            // assert
            assertThat(charged.balance()).isEqualTo(new Money(10_000));
            assertThat(balance.balance()).isEqualTo(new Money(0));
        }

        @DisplayName("충전액이 0이면 INVALID_INPUT 으로 거절하고, 기존 잔액은 그대로다.")
        @Test
        void rejectsWithInvalidInput_whenAmountIsZero() {
            // arrange
            PointBalance balance = new PointBalance(new Money(5_000));

            // act
            CoreException exception = assertThrows(CoreException.class, () -> balance.charge(new Money(0)));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(balance.balance()).isEqualTo(new Money(5_000));
        }

        @DisplayName("충전 후 잔액이 표현 범위를 넘으면 INVALID_INPUT 으로 거절하고, 기존 잔액은 그대로다.")
        @Test
        void rejectsWithInvalidInput_whenChargedBalanceOverflows() {
            // arrange
            PointBalance balance = new PointBalance(new Money(Long.MAX_VALUE));

            // act
            CoreException exception = assertThrows(CoreException.class, () -> balance.charge(new Money(1)));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(balance.balance()).isEqualTo(new Money(Long.MAX_VALUE));
        }
    }

    @DisplayName("포인트로 결제할 때,")
    @Nested
    class Pay {
        @DisplayName("결제액을 뺀 새 잔액을 반환하고, 기존 잔액은 그대로다.")
        @Test
        void returnsRemainingBalance_andKeepsOriginal() {
            // arrange
            PointBalance balance = new PointBalance(new Money(10_000));

            // act
            PointBalance paid = balance.pay(new Money(7_000));

            // assert
            assertThat(paid.balance()).isEqualTo(new Money(3_000));
            assertThat(balance.balance()).isEqualTo(new Money(10_000));
        }

        @DisplayName("결제액이 잔액과 같으면 전액 결제되어 잔액이 0이 된다.")
        @Test
        void returnsZeroBalance_whenAmountIsEqualToBalance() {
            // arrange
            PointBalance balance = new PointBalance(new Money(10_000));

            // act
            PointBalance paid = balance.pay(new Money(10_000));

            // assert
            assertThat(paid.balance()).isEqualTo(new Money(0));
        }

        @DisplayName("결제액이 0이면 INVALID_INPUT 으로 거절하고, 기존 잔액은 그대로다.")
        @Test
        void rejectsWithInvalidInput_whenAmountIsZero() {
            // arrange
            PointBalance balance = new PointBalance(new Money(10_000));

            // act
            CoreException exception = assertThrows(CoreException.class, () -> balance.pay(new Money(0)));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(balance.balance()).isEqualTo(new Money(10_000));
        }

        @DisplayName("결제액이 잔액보다 크면 INSUFFICIENT_POINT 로 거절하고, 기존 잔액은 그대로다.")
        @Test
        void rejectsWithInsufficientPoint_whenAmountIsGreaterThanBalance() {
            // arrange
            PointBalance balance = new PointBalance(new Money(3_000));

            // act
            CoreException exception = assertThrows(CoreException.class, () -> balance.pay(new Money(7_000)));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INSUFFICIENT_POINT);
            assertThat(balance.balance()).isEqualTo(new Money(3_000));
        }
    }
}
