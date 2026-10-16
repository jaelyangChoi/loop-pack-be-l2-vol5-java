package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderLinesTest {

    private void assertInvalidInput(Runnable action) {
        CoreException exception = assertThrows(CoreException.class, action::run);
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
    }

    @DisplayName("주문 요청 품목 목록을 만들 때,")
    @Nested
    class Create {
        @DisplayName("서로 다른 상품이면 요청 순서대로 상품 id 를 돌려준다.")
        @Test
        void keepsRequestOrder_whenProductsAreDistinct() {
            // act
            OrderLines lines = new OrderLines(List.of(new OrderLine(5L, 1), new OrderLine(3L, 2), new OrderLine(9L, 1)));

            // assert
            assertThat(lines.productIds()).containsExactly(5L, 3L, 9L);
            assertThat(lines.values()).hasSize(3);
        }

        @DisplayName("목록이 null 이거나 비어 있으면 INVALID_INPUT 으로 거절한다.")
        @Test
        void throwsInvalidInput_whenNullOrEmpty() {
            assertInvalidInput(() -> new OrderLines(null));
            assertInvalidInput(() -> new OrderLines(List.of()));
        }

        @DisplayName("목록에 null 항목이 있으면 INVALID_INPUT 으로 거절한다.")
        @Test
        void throwsInvalidInput_whenContainsNull() {
            List<OrderLine> withNull = new ArrayList<>();
            withNull.add(new OrderLine(1L, 1));
            withNull.add(null);

            assertInvalidInput(() -> new OrderLines(withNull));
        }

        @DisplayName("같은 상품이 두 번 나오면 수량이 달라도 INVALID_INPUT 으로 거절한다.")
        @Test
        void throwsInvalidInput_whenProductIsDuplicated() {
            // act
            CoreException exception = assertThrows(
                CoreException.class,
                () -> new OrderLines(List.of(new OrderLine(3L, 1), new OrderLine(4L, 1), new OrderLine(3L, 5)))
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(exception.getMessage()).contains("중복");
        }

        @DisplayName("만든 뒤 원본 목록을 바꿔도 영향을 받지 않는다.")
        @Test
        void isNotAffected_whenSourceListChanges() {
            // arrange
            List<OrderLine> source = new ArrayList<>(List.of(new OrderLine(1L, 1)));
            OrderLines lines = new OrderLines(source);

            // act
            source.add(new OrderLine(1L, 1));

            // assert
            assertThat(lines.values()).hasSize(1);
        }
    }
}
