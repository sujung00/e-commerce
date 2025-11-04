package com.hhplus.ecommerce.domain;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Product 도메인 엔티티
 * 상품 정보 및 재고 상태 관리
 * total_stock은 product_options의 재고 합계로 계산됨
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    private Long productId;
    private String productName;
    private String description;
    private Long price;
    private Integer totalStock;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}