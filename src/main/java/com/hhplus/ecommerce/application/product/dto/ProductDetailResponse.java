package com.hhplus.ecommerce.application.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 상세 조회 응답 (Application layer 내부 DTO)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailResponse {
    private Long productId;
    private String productName;
    private String description;
    private Long price;
    private Integer totalStock;
    private String status;
    private List<ProductOptionResponse> options;
    private LocalDateTime createdAt;
}