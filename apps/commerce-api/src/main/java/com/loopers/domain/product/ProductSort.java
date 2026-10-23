package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 고객 상품 목록의 정렬 기준. 동률은 모두 상품 id 내림차순으로 이어 정렬한다.
 *
 * <p>요청 값은 소문자 snake_case 문자열({@code latest}, {@code price_asc}, {@code likes_desc})이며 정확히 일치해야 한다.
 */
public enum ProductSort {
    /** 최신순. id 내림차순이 곧 최신순이다. */
    LATEST("latest"),
    /** 가격 낮은 순. 가격이 같으면 id 내림차순. */
    PRICE_ASC("price_asc"),
    /** 좋아요 많은 순. 좋아요 수가 같으면 id 내림차순. */
    LIKES_DESC("likes_desc");

    private final String value;

    ProductSort(String value) {
        this.value = value;
    }

    /** 요청에 쓰는 문자열 값({@code latest} 등). */
    public String value() {
        return value;
    }

    public static ProductSort from(String value) {
        return Arrays.stream(values())
            .filter(sort -> sort.value.equals(value))
            .findFirst()
            .orElseThrow(() -> new CoreException(
                ErrorType.INVALID_INPUT,
                "sort 는 " + Arrays.stream(values()).map(sort -> sort.value).collect(Collectors.joining(", "))
                    + " 중 하나여야 합니다."
            ));
    }
}
