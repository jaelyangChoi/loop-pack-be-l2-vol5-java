package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.paging.PageRequests;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 유스케이스.
 *
 * <p>요청자 id 는 앞단의 식별 필터({@code CustomerIdentityFilter})를 거친, 존재하는 사용자의 id 여야 한다. 신뢰 경계는
 * 그 필터 한 곳이라 여기서는 존재를 다시 조회하지 않는다. 다만 식별이 필요한 경로로 등록하지 않으면 {@code null} 이
 * 들어오므로, 클라이언트 잘못이나 조용한 성공으로 보이지 않게 서버 설정 실수로 거절한다.
 */
@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final ProductService productService;
    private final LikeService likeService;

    /** 새 좋아요는 삭제되지 않은 상품에만 가능하다. 이미 좋아요한 상품이면 그대로 성공한다. */
    @Transactional
    public void like(Long userId, Long productId) {
        requireRequester(userId);
        productService.get(productId);
        likeService.like(userId, productId);
    }

    /** 상품은 조회하지 않는다. 삭제된 상품이어도, 관계가 없어도 성공해서 자신의 기존 좋아요를 항상 취소할 수 있다. */
    @Transactional
    public void unlike(Long userId, Long productId) {
        requireRequester(userId);
        likeService.unlike(userId, productId);
    }

    /**
     * 내 좋아요 상품 목록. 소유자가 URL 에 드러나 있어(userId) 요청자와 다르면 조회 없이 비교만으로 거절한다.
     * 그래서 존재하지 않는 userId 여도 사용자 존재 여부가 드러나지 않는다 (D-11).
     */
    @Transactional(readOnly = true)
    public Page<ProductInfo> getMyLikes(Long requesterId, Long userId, int page, int size) {
        requireRequester(requesterId);
        if (!userId.equals(requesterId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 좋아요 목록만 조회할 수 있습니다.");
        }
        PageRequest pageRequest = PageRequests.of(page, size);
        return productService.getLikedList(userId, pageRequest).map(ProductInfo::from);
    }

    private void requireRequester(Long requesterId) {
        if (requesterId == null) {
            throw new CoreException(
                ErrorType.INTERNAL_ERROR,
                "요청자 식별 정보가 없습니다. 식별이 필요한 경로로 등록됐는지 확인하세요."
            );
        }
    }
}
