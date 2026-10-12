package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.paging.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product V1 API", description = "고객 상품 조회 API 입니다.")
public interface ProductApiSpec {

    @Operation(
        summary = "상품 목록 조회",
        description = "삭제되지 않은 상품을 조회합니다. 요청자를 식별하지 않습니다. 정렬은 latest(최신순, 기본) · price_asc(가격 낮은순) · likes_desc(좋아요 많은순)이며 "
            + "동률은 상품 id 내림차순입니다. 존재하지 않는 brandId 는 오류가 아니라 빈 목록입니다."
    )
    ApiResponse<PageResponse<ProductDto.ProductResponse>> getList(
        @Parameter(name = "brandId", description = "지정하면 해당 브랜드의 상품만 조회 (선택)")
        Long brandId,
        @Parameter(name = "sort", description = "latest | price_asc | likes_desc (기본 latest)")
        String sort,
        @Parameter(name = "page", description = PageRequests.PAGE_PARAM_DESCRIPTION)
        int page,
        @Parameter(name = "size", description = PageRequests.SIZE_PARAM_DESCRIPTION)
        int size
    );

    @Operation(
        summary = "상품 상세 조회",
        description = "삭제되지 않은 상품을 브랜드 정보와 좋아요 수와 함께 조회합니다. 요청자를 식별하지 않으며 재고는 노출하지 않습니다."
    )
    ApiResponse<ProductDto.ProductResponse> get(
        @Parameter(name = "productId", description = "조회할 상품의 ID")
        Long productId
    );
}
