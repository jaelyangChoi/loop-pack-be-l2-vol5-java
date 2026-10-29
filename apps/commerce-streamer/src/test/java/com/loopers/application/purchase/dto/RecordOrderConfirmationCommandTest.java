package com.loopers.application.purchase.dto;

import com.loopers.domain.purchase.PurchaseLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordOrderConfirmationCommandTest {

    private static final ZonedDateTime CONFIRMED_AT = ZonedDateTime.parse("2026-09-07T01:00:00Z");

    @DisplayName("같은 상품은 합산하고 상품 ID 순으로 정렬해, 품목 순서가 달라도 같은 사실이 된다.")
    @Test
    void mergesAndSortsLines() {
        // act
        RecordOrderConfirmationCommand command = new RecordOrderConfirmationCommand(
            "order-confirmed:v1:42", 42L, CONFIRMED_AT,
            List.of(new PurchaseLine(8L, 1), new PurchaseLine(7L, 1), new PurchaseLine(7L, 1))
        );

        // assert
        assertThat(command.lines()).containsExactly(new PurchaseLine(7L, 2), new PurchaseLine(8L, 1));
    }

    @DisplayName("품목이 없거나 수량이 1 미만이면 거절한다.")
    @Test
    void rejectsEmptyLinesOrNonPositiveQuantity() {
        assertThatThrownBy(() -> new RecordOrderConfirmationCommand("e", 42L, CONFIRMED_AT, List.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PurchaseLine(7L, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
