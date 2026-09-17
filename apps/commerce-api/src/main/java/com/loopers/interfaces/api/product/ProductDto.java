package com.loopers.interfaces.api.product;

public class ProductDto {
    public record ProductResponse(Long productId, String name, Long price, BrandSummary brand, Long likeCount) {}

    public record BrandSummary(Long brandId, String name) {}
}
