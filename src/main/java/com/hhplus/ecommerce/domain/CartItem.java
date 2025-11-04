package com.hhplus.ecommerce.domain;

import lombok.*;

import java.time.LocalDateTime;

/**
 * CartItem 도메인 엔티티
 * 쇼핑 카트의 라인 항목 (옵션 필수)
 * subtotal은 unit_price * quantity로 계산되는 계산 필드
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {
    private Long cartItemId;
    private Long cartId;
    private Long productId;
    private Long optionId;
    private Integer quantity;
    private Long unitPrice;
    private Long subtotal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}