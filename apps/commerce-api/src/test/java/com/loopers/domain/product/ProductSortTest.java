package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductSortTest {

    @DisplayName("정렬 값을 변환할 때,")
    @Nested
    class From {
        @DisplayName("latest, price_asc, likes_desc 를 각각 해당 정렬로 변환한다.")
        @ParameterizedTest
        @CsvSource({"latest, LATEST", "price_asc, PRICE_ASC", "likes_desc, LIKES_DESC"})
        void convertsKnownValues(String value, ProductSort expected) {
            // act & assert
            assertThat(ProductSort.from(value)).isEqualTo(expected);
        }

        @DisplayName("알 수 없는 값이면 INVALID_INPUT 으로 거절한다. 대소문자도 정확히 일치해야 한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "abc", "LATEST", "Price_Asc", "price_desc", "likes"})
        void rejectsWithInvalidInput_whenValueIsUnknown(String value) {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> ProductSort.from(value));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
        }

        @DisplayName("거절 메시지에 사용할 수 있는 값을 알려 준다.")
        @Test
        void tellsAvailableValues() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> ProductSort.from("abc"));

            // assert
            assertThat(exception.getCustomMessage())
                .contains("latest")
                .contains("price_asc")
                .contains("likes_desc");
        }
    }
}
