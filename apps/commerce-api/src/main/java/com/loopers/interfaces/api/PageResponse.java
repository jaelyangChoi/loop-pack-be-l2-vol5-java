package com.loopers.interfaces.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 목록 API 의 공통 응답 형식. 항목과 함께 페이지 정보(번호·크기·전체 개수·전체 페이지 수)를 담는다.
 *
 * @param <T> 응답에 담을 항목 타입. 어떤 필드를 노출할지는 각 API 의 Dto 가 정한다.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
            page.getContent().stream().map(mapper).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
