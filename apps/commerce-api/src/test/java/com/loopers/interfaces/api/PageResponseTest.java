package com.loopers.interfaces.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @DisplayName("Page 를 응답으로 바꿀 때, 각 항목을 변환하고 페이지 정보를 그대로 담는다.")
    @Test
    void mapsContent_andKeepsPageInfo() {
        // arrange: 전체 5건 중 두 번째 페이지(size 2)의 2건
        PageImpl<Integer> page = new PageImpl<>(List.of(3, 4), PageRequest.of(1, 2), 5);

        // act
        PageResponse<String> response = PageResponse.from(page, number -> "item-" + number);

        // assert
        assertThat(response.content()).containsExactly("item-3", "item-4");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @DisplayName("항목이 없는 Page 는 빈 목록으로 변환한다.")
    @Test
    void returnsEmptyContent_whenPageIsEmpty() {
        // arrange
        PageImpl<Integer> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        // act
        PageResponse<String> response = PageResponse.from(page, String::valueOf);

        // assert
        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }
}
