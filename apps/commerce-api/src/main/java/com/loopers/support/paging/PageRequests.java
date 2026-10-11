package com.loopers.support.paging;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.data.domain.PageRequest;

/**
 * 목록 API 의 페이지 요청 규칙을 한 곳에 모은다. page 는 0 이상, size 는 1 이상 100 이하이다.
 *
 * <p>정렬은 담지 않는다. 목록마다 정렬 기준이 다르고, 지금은 Repository 가 정렬을 고정하기 때문이다.
 */
public final class PageRequests {

    private static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {
    }

    public static PageRequest of(int page, int size) {
        if (page < 0) {
            throw new CoreException(ErrorType.INVALID_INPUT, "page 는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new CoreException(ErrorType.INVALID_INPUT, "size 는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }
        return PageRequest.of(page, size);
    }
}
