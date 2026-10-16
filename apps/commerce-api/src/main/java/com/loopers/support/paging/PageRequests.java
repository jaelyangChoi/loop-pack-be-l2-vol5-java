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

    public static final int MAX_PAGE_SIZE = 100;

    // 요청 파라미터의 기본값과 문서 설명. 어노테이션 값으로 쓰려고 컴파일 타임 상수(문자열)로 둔다.
    public static final String DEFAULT_PAGE = "0";
    public static final String DEFAULT_SIZE = "20";
    public static final String PAGE_PARAM_DESCRIPTION = "0부터 시작하는 페이지 번호 (기본 " + DEFAULT_PAGE + ")";
    public static final String SIZE_PARAM_DESCRIPTION = "페이지 크기 1~" + MAX_PAGE_SIZE + " (기본 " + DEFAULT_SIZE + ")";

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
