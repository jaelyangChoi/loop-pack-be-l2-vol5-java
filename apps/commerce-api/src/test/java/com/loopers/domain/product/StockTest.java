package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockTest {

    @DisplayName("재고를 생성할 때,")
    @Nested
    class Create {
        @DisplayName("음수이면 INVALID_INPUT 으로 거절한다.")
        @Test
        void rejectsWithInvalidInput_whenRemainingIsNegative() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> new Stock(-1));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
        }

        @DisplayName("0이면 품절 상태의 재고로 생성된다.")
        @Test
        void createsStock_whenRemainingIsZero() {
            // act
            Stock stock = new Stock(0);

            // assert
            assertThat(stock.remaining()).isZero();
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class Decrease {
        @DisplayName("보유 재고보다 많은 수량이면 OUT_OF_STOCK 으로 거절하고, 기존 재고는 그대로다.")
        @Test
        void rejectsWithOutOfStock_whenQuantityIsGreaterThanRemaining() {
            // arrange
            Stock stock = new Stock(5);

            // act
            CoreException exception = assertThrows(CoreException.class, () -> stock.decrease(6));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.OUT_OF_STOCK);
            assertThat(stock.remaining()).isEqualTo(5);
        }

        @DisplayName("보유 재고 이하의 수량이면 차감된 새 재고를 반환하고, 기존 재고는 그대로다.")
        @Test
        void returnsDecreasedStock_whenQuantityIsLessThanRemaining() {
            // arrange
            Stock stock = new Stock(5);

            // act
            Stock decreased = stock.decrease(2);

            // assert
            assertThat(decreased.remaining()).isEqualTo(3);
            assertThat(stock.remaining()).isEqualTo(5);
        }

        @DisplayName("보유 재고와 같은 수량이면 전량 차감되어 재고가 0이 된다.")
        @Test
        void returnsZeroStock_whenQuantityIsEqualToRemaining() {
            // arrange
            Stock stock = new Stock(5);

            // act
            Stock decreased = stock.decrease(5);

            // assert
            assertThat(decreased.remaining()).isZero();
        }

        @DisplayName("0 이하의 수량이면 INVALID_INPUT 으로 거절하고, 기존 재고는 그대로다.")
        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void rejectsWithInvalidInput_whenQuantityIsNotPositive(int quantity) {
            // arrange
            Stock stock = new Stock(5);

            // act
            CoreException exception = assertThrows(CoreException.class, () -> stock.decrease(quantity));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(stock.remaining()).isEqualTo(5);
        }
    }
}
