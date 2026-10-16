package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
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
@RequestMapping("/api/v1/products")
public class ProductController implements ProductApiSpec {

    private final ProductFacade productFacade;

    @GetMapping
    @Override
    public ApiResponse<PageResponse<ProductDto.ProductResponse>> getList(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "latest") String sort,
        @RequestParam(defaultValue = PageRequests.DEFAULT_PAGE) int page,
        @RequestParam(defaultValue = PageRequests.DEFAULT_SIZE) int size
    ) {
        return ApiResponse.success(
            PageResponse.from(productFacade.getList(brandId, sort, page, size), ProductDto.ProductResponse::from)
        );
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductDto.ProductResponse> get(@PathVariable Long productId) {
        return ApiResponse.success(ProductDto.ProductResponse.from(productFacade.get(productId)));
    }
}
