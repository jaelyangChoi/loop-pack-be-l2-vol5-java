package com.loopers.interfaces.api.admin.brand;

import com.loopers.application.brand.BrandInfo;
import org.springframework.data.domain.Page;

import java.time.ZonedDateTime;
import java.util.List;

public class AdminBrandDto {
    // 이름의 누락·공백·길이는 Brand 가 판단한다. 등록과 수정이 같은 요청 형식을 쓴다.
    public record BrandRequest(String name) {}

    public record BrandResponse(Long id, String name, ZonedDateTime createdAt, ZonedDateTime updatedAt) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(info.id(), info.name(), info.createdAt(), info.updatedAt());
        }
    }

    public record BrandPageResponse(
        List<BrandResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static BrandPageResponse from(Page<BrandInfo> page) {
            return new BrandPageResponse(
                page.getContent().stream().map(BrandResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }
}
