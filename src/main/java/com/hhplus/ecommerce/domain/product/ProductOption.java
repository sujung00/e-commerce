package com.hhplus.ecommerce.domain.product;

import lombok.*;

import java.time.LocalDateTime;

/**
 * ProductOption 도메인 엔티티
 * 상품의 옵션(색상, 사이즈 등)별 재고를 관리
 * 낙관적 락(version)을 사용하여 동시성 제어
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductOption {
    private Long optionId;
    private Long productId;
    private String name;
    private Integer stock;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
