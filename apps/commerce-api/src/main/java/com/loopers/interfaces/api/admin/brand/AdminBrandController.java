package com.loopers.interfaces.api.admin.brand;

import com.loopers.application.brand.AdminBrandFacade;
import com.loopers.interfaces.api.ApiResponse;
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
@RequestMapping("/api-admin/v1/brands")
public class AdminBrandController implements AdminBrandApiSpec {

    private final AdminBrandFacade adminBrandFacade;

    @PostMapping
    @Override
    public ApiResponse<AdminBrandDto.BrandResponse> create(@RequestBody AdminBrandDto.BrandRequest request) {
        return ApiResponse.success(AdminBrandDto.BrandResponse.from(adminBrandFacade.create(request.name())));
    }

    @GetMapping
    @Override
    public ApiResponse<AdminBrandDto.BrandPageResponse> getList(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(AdminBrandDto.BrandPageResponse.from(adminBrandFacade.getList(page, size)));
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<AdminBrandDto.BrandResponse> get(@PathVariable Long brandId) {
        return ApiResponse.success(AdminBrandDto.BrandResponse.from(adminBrandFacade.get(brandId)));
    }

    @PutMapping("/{brandId}")
    @Override
    public ApiResponse<AdminBrandDto.BrandResponse> update(
        @PathVariable Long brandId,
        @RequestBody AdminBrandDto.BrandRequest request
    ) {
        return ApiResponse.success(AdminBrandDto.BrandResponse.from(adminBrandFacade.update(brandId, request.name())));
    }

    @DeleteMapping("/{brandId}")
    @Override
    public ApiResponse<Object> delete(@PathVariable Long brandId) {
        adminBrandFacade.delete(brandId);
        return ApiResponse.success();
    }
}
