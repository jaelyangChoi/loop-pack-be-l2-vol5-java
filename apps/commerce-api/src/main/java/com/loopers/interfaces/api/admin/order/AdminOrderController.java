package com.loopers.interfaces.api.admin.order;

import com.loopers.application.order.AdminOrderFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.paging.PageRequests;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class AdminOrderController implements AdminOrderApiSpec {

    private final AdminOrderFacade adminOrderFacade;

    @GetMapping
    @Override
    public ApiResponse<PageResponse<AdminOrderDto.OrderSummaryResponse>> getList(
        @RequestParam(required = false) Long userId,
        @RequestParam(defaultValue = PageRequests.DEFAULT_PAGE) int page,
        @RequestParam(defaultValue = PageRequests.DEFAULT_SIZE) int size
    ) {
        return ApiResponse.success(
            PageResponse.from(adminOrderFacade.getList(userId, page, size), AdminOrderDto.OrderSummaryResponse::from)
        );
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<AdminOrderDto.OrderResponse> get(@PathVariable Long orderId) {
        return ApiResponse.success(AdminOrderDto.OrderResponse.from(adminOrderFacade.get(orderId)));
    }
}
