package com.loopers.application.product;

import java.util.List;

/**
 * 상품 목록 캐시에 담는 값. 고객 목록 응답에서 좋아요 수를 뺀 나머지다.
 *
 * <p>좋아요 수는 가장 잦은 쓰기라 담지 않는다(D-38). 캐시를 쓸 때마다 카운터 테이블에서 읽어 합친다.
 */
public record ProductListSnapshot(List<Item> items, int page, int size, long totalElements) {

    public ProductListSnapshot {
        // 깨진 값(items 누락)을 정상 목록으로 읽지 않도록 만들 때 거절한다.
        items = List.copyOf(items);
    }

    public record Item(Long id, String name, long price, Brand brand) {
    }

    public record Brand(Long id, String name) {
    }
}
