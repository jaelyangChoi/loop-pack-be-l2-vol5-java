package com.loopers.interfaces.api.admin.order;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Order V1 API", description = "관리자 주문 목록·상세 조회 API 입니다.")
public interface AdminOrderApiSpec {

    @Operation(
        summary = "주문 목록 조회",
        description = "주문을 최신순으로 요약해 조회합니다. userId 를 주면 그 구매자의 주문만 조회하고, 주문이 없거나 없는 구매자는 "
            + "오류가 아니라 빈 목록입니다. 항목은 구매자 id, 상태, 합계, 품목 수, 결제 결과(확정된 주문만), 주문 시각입니다."
    )
    ApiResponse<PageResponse<AdminOrderDto.OrderSummaryResponse>> getList(
        @Parameter(name = "userId", description = "지정하면 해당 구매자의 주문만 조회 (선택)")
        Long userId,
        @Parameter(name = "page", description = "0부터 시작하는 페이지 번호 (기본 0)")
        int page,
        @Parameter(name = "size", description = "페이지 크기 1~100 (기본 20)")
        int size
    );

    @Operation(
        summary = "주문 상세 조회",
        description = "구매자와 무관하게 주문 하나를 조회합니다. 구매자 id, 품목(주문 당시 상품명·단가), 합계, 상태, "
            + "결제 결과(확정된 주문만), 주문 시각을 담습니다. 없는 주문은 404입니다."
    )
    ApiResponse<AdminOrderDto.OrderResponse> get(
        @Parameter(name = "orderId", description = "조회할 주문의 ID")
        Long orderId
    );
}
