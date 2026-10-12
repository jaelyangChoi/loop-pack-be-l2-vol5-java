package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order V1 API", description = "고객 주문 API 입니다.")
public interface OrderApiSpec {

    @Operation(
        summary = "주문 생성",
        description = "여러 품목으로 확정 전(DRAFT) 주문을 만듭니다. 품목에는 주문 당시의 상품명·단가가 저장되고 재고와 포인트는 차감하지 않습니다. "
            + "품목은 하나 이상이어야 하고 수량은 1 이상이며 같은 상품은 한 번만 담을 수 있습니다(400). "
            + "없거나 삭제된 상품이 있으면 404이며 메시지에 해당 상품 ID를 모두 담습니다."
    )
    @Parameter(name = "X-USER-ID", in = ParameterIn.HEADER, required = true, description = "요청자(사용자) ID")
    ApiResponse<OrderDto.OrderResponse> create(
        @Parameter(hidden = true)
        Long userId,
        OrderDto.CreateRequest request
    );
}
