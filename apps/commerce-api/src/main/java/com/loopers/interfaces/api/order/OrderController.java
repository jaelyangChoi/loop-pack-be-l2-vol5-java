package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController implements OrderApiSpec {

    private final OrderFacade orderFacade;

    @PostMapping
    @Override
    public ApiResponse<OrderDto.OrderResponse> create(
        @AuthenticationPrincipal Long userId,
        @Valid @RequestBody OrderDto.CreateRequest request
    ) {
        return ApiResponse.success(OrderDto.OrderResponse.from(orderFacade.create(userId, request.toCommands())));
    }

    @GetMapping
    @Override
    public ApiResponse<PageResponse<OrderDto.OrderSummaryResponse>> getList(
        @AuthenticationPrincipal Long userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
            PageResponse.from(orderFacade.getMyList(userId, page, size), OrderDto.OrderSummaryResponse::from)
        );
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderDto.OrderResponse> get(
        @AuthenticationPrincipal Long userId,
        @PathVariable Long orderId
    ) {
        return ApiResponse.success(OrderDto.OrderResponse.from(orderFacade.getMine(userId, orderId)));
    }

    @PostMapping("/{orderId}/confirm")
    @Override
    public ApiResponse<OrderDto.OrderResponse> confirm(
        @AuthenticationPrincipal Long userId,
        @PathVariable Long orderId
    ) {
        return ApiResponse.success(OrderDto.OrderResponse.from(orderFacade.confirm(userId, orderId)));
    }
}
