package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 등록·취소는 상품 하위 경로, 목록은 사용자 하위 경로라 클래스 공통 경로 없이 메소드마다 전체 경로를 둔다.
@RequiredArgsConstructor
@RestController
public class LikeController implements LikeApiSpec {

    private final LikeFacade likeFacade;

    @PostMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Object> like(
        @AuthenticationPrincipal Long userId,
        @PathVariable Long productId
    ) {
        likeFacade.like(userId, productId);
        return ApiResponse.success();
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Object> unlike(
        @AuthenticationPrincipal Long userId,
        @PathVariable Long productId
    ) {
        likeFacade.unlike(userId, productId);
        return ApiResponse.success();
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    @Override
    public ApiResponse<PageResponse<LikeDto.LikedProductResponse>> getMyLikes(
        @AuthenticationPrincipal Long requesterId,
        @PathVariable Long userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
            PageResponse.from(likeFacade.getMyLikes(requesterId, userId, page, size), LikeDto.LikedProductResponse::from)
        );
    }
}
