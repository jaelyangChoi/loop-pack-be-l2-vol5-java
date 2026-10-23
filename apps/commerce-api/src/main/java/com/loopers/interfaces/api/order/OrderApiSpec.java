package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.paging.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

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

    @Operation(
        summary = "주문 결제 시작(확정 요청)",
        description = "본인의 확정 전(DRAFT) 주문의 PG 결제를 시작합니다. 주문은 결제 대기(PAYMENT_PENDING)가 되고 품목마다 재고를 "
            + "확보하며 포인트는 차감하지 않습니다. 결제액은 요청이 아니라 주문 당시 합계입니다. 주문은 PG 성공을 확인한 뒤에야 "
            + "확정(CONFIRMED)됩니다. 응답이 202이면 처리 중이거나 결과를 아직 모르는 것이므로 결제를 새로 요청하지 말고 "
            + "links.order 로 주문 상태를 조회하세요. 200이면 PG 결과로 끝난 시도입니다(실패한 시도도 포함). "
            + "같은 주문에 같은 paymentAttemptKey 를 다시 보내면 저장된 결과를 돌려주고 PG 에 다시 요청하지 않습니다. 다른 키이거나 "
            + "다른 주문에 쓰인 키는 409(PAYMENT_ATTEMPT_CONFLICT)이고, 결제 시도는 주문당 한 번뿐이라 실패한 뒤에도 새 키로 "
            + "다시 시도할 수 없습니다. 형식 오류는 400, 없는 주문과 다른 사용자의 주문은 404, 이미 확정된 주문·삭제된 상품·재고 "
            + "부족은 409(각각 ORDER_ALREADY_CONFIRMED, PRODUCT_UNAVAILABLE, OUT_OF_STOCK)이며 이때 PG 에 요청하지 않습니다."
    )
    @Parameter(name = "X-USER-ID", in = ParameterIn.HEADER, required = true, description = "요청자(사용자) ID")
    ResponseEntity<ApiResponse<OrderDto.PaymentStartResponse>> confirm(
        @Parameter(hidden = true)
        Long userId,
        @Parameter(name = "orderId", description = "결제를 시작할 주문의 ID")
        Long orderId,
        OrderDto.ConfirmRequest request
    );

    @Operation(
        summary = "내 주문 목록 조회",
        description = "내 주문을 최신순으로 요약해 조회합니다. 항목은 상태, 합계, 품목 수, 결제 결과(확정된 주문만), 주문 시각이며 "
            + "품목은 상세 조회에서 봅니다."
    )
    @Parameter(name = "X-USER-ID", in = ParameterIn.HEADER, required = true, description = "요청자(사용자) ID")
    ApiResponse<PageResponse<OrderDto.OrderSummaryResponse>> getList(
        @Parameter(hidden = true)
        Long userId,
        @Parameter(name = "page", description = PageRequests.PAGE_PARAM_DESCRIPTION)
        int page,
        @Parameter(name = "size", description = PageRequests.SIZE_PARAM_DESCRIPTION)
        int size
    );

    @Operation(
        summary = "내 주문 상세 조회",
        description = "내 주문의 품목(주문 당시 상품명·단가), 합계, 상태, 결제 결과(확정된 주문만), 주문 시각을 조회합니다. "
            + "없는 주문과 다른 사용자의 주문은 구분하지 않고 404입니다."
    )
    @Parameter(name = "X-USER-ID", in = ParameterIn.HEADER, required = true, description = "요청자(사용자) ID")
    ApiResponse<OrderDto.OrderResponse> get(
        @Parameter(hidden = true)
        Long userId,
        @Parameter(name = "orderId", description = "조회할 주문의 ID")
        Long orderId
    );
}
