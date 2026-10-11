package com.loopers.interfaces.api.admin.brand;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Brand V1 API", description = "관리자 브랜드 등록·조회·수정·삭제 API 입니다.")
public interface AdminBrandApiSpec {

    @Operation(summary = "브랜드 등록", description = "이름(앞뒤 공백 제거 후 1~50자)으로 브랜드를 등록합니다.")
    ApiResponse<AdminBrandDto.BrandResponse> create(AdminBrandDto.BrandRequest request);

    @Operation(summary = "브랜드 목록 조회", description = "삭제되지 않은 브랜드를 최신순으로 페이지 단위로 조회합니다.")
    ApiResponse<PageResponse<AdminBrandDto.BrandResponse>> getList(
        @Schema(name = "페이지", description = "0부터 시작하는 페이지 번호 (기본 0)")
        int page,
        @Schema(name = "크기", description = "페이지 크기 1~100 (기본 20)")
        int size
    );

    @Operation(summary = "브랜드 상세 조회", description = "삭제되지 않은 브랜드를 조회합니다.")
    ApiResponse<AdminBrandDto.BrandResponse> get(
        @Schema(name = "브랜드 ID", description = "조회할 브랜드의 ID")
        Long brandId
    );

    @Operation(summary = "브랜드 수정", description = "브랜드 이름을 수정합니다.")
    ApiResponse<AdminBrandDto.BrandResponse> update(
        @Schema(name = "브랜드 ID", description = "수정할 브랜드의 ID")
        Long brandId,
        AdminBrandDto.BrandRequest request
    );

    @Operation(summary = "브랜드 삭제", description = "브랜드를 삭제합니다. 삭제된 브랜드는 조회·수정 대상이 아닙니다.")
    ApiResponse<Object> delete(
        @Schema(name = "브랜드 ID", description = "삭제할 브랜드의 ID")
        Long brandId
    );
}
