package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.paging.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Like V1 API", description = "상품 좋아요 등록·취소와 내 좋아요 목록 API 입니다.")
public interface LikeApiSpec {

    @Operation(
        summary = "좋아요 등록",
        description = "삭제되지 않은 상품에 좋아요를 등록합니다. 이미 좋아요한 상품이어도 성공하며 관계는 하나만 유지됩니다."
    )
    @Parameter(name = "X-USER-ID", in = ParameterIn.HEADER, required = true, description = "요청자(사용자) ID")
    ApiResponse<Object> like(
        @Parameter(hidden = true)
        Long userId,
        @Parameter(name = "productId", description = "좋아요할 상품의 ID")
        Long productId
    );

    @Operation(
        summary = "좋아요 취소",
        description = "자신의 좋아요를 취소합니다. 관계가 없어도, 상품이 삭제됐어도 성공합니다."
    )
    @Parameter(name = "X-USER-ID", in = ParameterIn.HEADER, required = true, description = "요청자(사용자) ID")
    ApiResponse<Object> unlike(
        @Parameter(hidden = true)
        Long userId,
        @Parameter(name = "productId", description = "좋아요를 취소할 상품의 ID")
        Long productId
    );

    @Operation(
        summary = "내 좋아요 목록 조회",
        description = "내가 좋아요한 삭제되지 않은 상품을 최근에 좋아요한 순으로 조회합니다. 다른 사용자의 userId 는 403 입니다."
    )
    @Parameter(name = "X-USER-ID", in = ParameterIn.HEADER, required = true, description = "요청자(사용자) ID")
    ApiResponse<PageResponse<LikeDto.LikedProductResponse>> getMyLikes(
        @Parameter(hidden = true)
        Long requesterId,
        @Parameter(name = "userId", description = "목록을 조회할 사용자의 ID (요청자와 같아야 함)")
        Long userId,
        @Parameter(name = "page", description = PageRequests.PAGE_PARAM_DESCRIPTION)
        int page,
        @Parameter(name = "size", description = PageRequests.SIZE_PARAM_DESCRIPTION)
        int size
    );
}
