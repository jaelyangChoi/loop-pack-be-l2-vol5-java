package com.loopers.interfaces.api.point;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Point V1 API", description = "포인트 충전과 잔액 조회 API 입니다.")
public interface PointApiSpec {

    @Operation(
        summary = "포인트 충전",
        description = "요청자의 포인트를 충전하고 충전 후 잔액을 반환합니다."
    )
    ApiResponse<PointDto.PointResponse> charge(
        @Schema(name = "요청자 ID", description = "X-USER-ID 헤더로 전달하는 요청자의 ID")
        Long userId,
        PointDto.ChargeRequest request
    );

    @Operation(
        summary = "포인트 잔액 조회",
        description = "요청자의 저장된 포인트 잔액을 조회합니다."
    )
    ApiResponse<PointDto.PointResponse> getPoint(
        @Schema(name = "요청자 ID", description = "X-USER-ID 헤더로 전달하는 요청자의 ID")
        Long userId
    );
}
