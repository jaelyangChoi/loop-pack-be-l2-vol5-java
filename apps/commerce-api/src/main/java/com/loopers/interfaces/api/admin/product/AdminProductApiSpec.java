package com.loopers.interfaces.api.admin.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.paging.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Product V1 API", description = "관리자 상품 등록·조회·수정·삭제와 재고 변경 API 입니다.")
public interface AdminProductApiSpec {

    @Operation(
        summary = "상품 등록",
        description = "삭제되지 않은 브랜드에 상품을 등록합니다. 이름은 앞뒤 공백 제거 후 1~100자, 가격은 1~1,000,000,000원, 재고는 0 이상입니다."
    )
    ApiResponse<AdminProductDto.ProductResponse> create(AdminProductDto.CreateRequest request);

    @Operation(summary = "상품 목록 조회", description = "삭제되지 않은 상품을 최신순으로 페이지 단위로 조회합니다.")
    ApiResponse<PageResponse<AdminProductDto.ProductResponse>> getList(
        @Parameter(name = "page", description = PageRequests.PAGE_PARAM_DESCRIPTION)
        int page,
        @Parameter(name = "size", description = PageRequests.SIZE_PARAM_DESCRIPTION)
        int size
    );

    @Operation(summary = "상품 상세 조회", description = "삭제되지 않은 상품을 조회합니다.")
    ApiResponse<AdminProductDto.ProductResponse> get(
        @Parameter(name = "productId", description = "조회할 상품의 ID")
        Long productId
    );

    @Operation(summary = "상품 수정", description = "상품의 이름과 가격을 수정합니다. 브랜드는 바꿀 수 없습니다.")
    ApiResponse<AdminProductDto.ProductResponse> update(
        @Parameter(name = "productId", description = "수정할 상품의 ID")
        Long productId,
        AdminProductDto.UpdateRequest request
    );

    @Operation(summary = "재고 변경", description = "상품의 재고를 지정한 최종 수량으로 바꿉니다. 0 이상이어야 합니다.")
    ApiResponse<AdminProductDto.ProductResponse> changeStock(
        @Parameter(name = "productId", description = "재고를 바꿀 상품의 ID")
        Long productId,
        AdminProductDto.StockRequest request
    );

    @Operation(summary = "상품 삭제", description = "상품을 삭제합니다. 삭제된 상품은 조회·수정 대상이 아닙니다.")
    ApiResponse<Object> delete(
        @Parameter(name = "productId", description = "삭제할 상품의 ID")
        Long productId
    );
}
