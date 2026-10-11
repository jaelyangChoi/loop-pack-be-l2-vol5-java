package com.loopers.interfaces.api.admin.product;

import com.loopers.application.product.AdminProductFacade;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class AdminProductController implements AdminProductApiSpec {

    private final AdminProductFacade adminProductFacade;

    @PostMapping
    @Override
    public ApiResponse<AdminProductDto.ProductResponse> create(
        @Valid @RequestBody AdminProductDto.CreateRequest request
    ) {
        return ApiResponse.success(AdminProductDto.ProductResponse.from(adminProductFacade.create(
            request.brandId(), request.name(), request.price(), request.stock()
        )));
    }

    @GetMapping
    @Override
    public ApiResponse<AdminProductDto.ProductPageResponse> getList(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(AdminProductDto.ProductPageResponse.from(adminProductFacade.getList(page, size)));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<AdminProductDto.ProductResponse> get(@PathVariable Long productId) {
        return ApiResponse.success(AdminProductDto.ProductResponse.from(adminProductFacade.get(productId)));
    }

    @PutMapping("/{productId}")
    @Override
    public ApiResponse<AdminProductDto.ProductResponse> update(
        @PathVariable Long productId,
        @Valid @RequestBody AdminProductDto.UpdateRequest request
    ) {
        return ApiResponse.success(AdminProductDto.ProductResponse.from(
            adminProductFacade.update(productId, request.name(), request.price())
        ));
    }

    @PutMapping("/{productId}/stock")
    @Override
    public ApiResponse<AdminProductDto.ProductResponse> changeStock(
        @PathVariable Long productId,
        @Valid @RequestBody AdminProductDto.StockRequest request
    ) {
        return ApiResponse.success(AdminProductDto.ProductResponse.from(
            adminProductFacade.changeStock(productId, request.stock())
        ));
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Object> delete(@PathVariable Long productId) {
        adminProductFacade.delete(productId);
        return ApiResponse.success();
    }
}
