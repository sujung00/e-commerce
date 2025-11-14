package com.hhplus.ecommerce.application.product.dto;

import com.hhplus.ecommerce.domain.product.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 상품 정보 응답 (Application layer 내부 DTO)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long productId;
    private String productName;
    private String description;
    private Long price;
    private Integer totalStock;
    private String status;
    private LocalDateTime createdAt;

    public static ProductResponse fromProduct(Product product) {
        return ProductResponse.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .description(product.getDescription())
                .price(product.getPrice())
                .totalStock(product.getTotalStock())
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .build();
    }
}