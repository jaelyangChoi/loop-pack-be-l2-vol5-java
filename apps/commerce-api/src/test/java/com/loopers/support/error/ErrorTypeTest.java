package com.loopers.support.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorTypeTest {

    @DisplayName("모든 에러 타입이 비어 있지 않은 기본 메시지를 가진다.")
    @Test
    void everyErrorTypeShouldHaveMessage() {
        for (ErrorType errorType : ErrorType.values()) {
            assertThat(errorType.getMessage())
                .as("ErrorType.%s 의 기본 메시지", errorType.name())
                .isNotBlank();
        }
    }

    @DisplayName("모든 에러 타입의 코드는 상수명과 같다.")
    @Test
    void everyErrorCodeShouldBeConstantName() {
        for (ErrorType errorType : ErrorType.values()) {
            assertThat(errorType.getCode())
                .as("ErrorType.%s 의 코드", errorType.name())
                .isEqualTo(errorType.name());
        }
    }
}
