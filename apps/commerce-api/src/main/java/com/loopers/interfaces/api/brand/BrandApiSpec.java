package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand V1 API", description = "고객 브랜드 조회 API 입니다.")
public interface BrandApiSpec {

    @Operation(
        summary = "브랜드 상세 조회",
        description = "삭제되지 않은 브랜드의 id 와 이름을 조회합니다. 요청자를 식별하지 않습니다."
    )
    ApiResponse<BrandDto.BrandResponse> get(
        @Parameter(name = "brandId", description = "조회할 브랜드의 ID")
        Long brandId
    );
}
