package com.hhplus.ecommerce.domain.cart;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Cart 도메인 엔티티
 * 사용자별 쇼핑 카트 (1:1 관계)
 * total_items와 total_price는 cart_items에서 계산되는 계산 필드
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cart {
    private Long cartId;
    private Long userId;
    private Integer totalItems;
    private Long totalPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}