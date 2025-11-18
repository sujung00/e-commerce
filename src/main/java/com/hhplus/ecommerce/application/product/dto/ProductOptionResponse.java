package com.hhplus.ecommerce.application.product.dto;

import com.hhplus.ecommerce.domain.product.ProductOption;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 옵션 응답 (Application layer 내부 DTO)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductOptionResponse {
    private Long optionId;
    private String name;
    private Integer stock;
    private Long version;  // 수정: Domain의 Long version과 일치

    public static ProductOptionResponse fromProductOption(ProductOption option) {
        return ProductOptionResponse.builder()
                .optionId(option.getOptionId())
                .name(option.getName())
                .stock(option.getStock())
                .version(option.getVersion())
                .build();
    }
}