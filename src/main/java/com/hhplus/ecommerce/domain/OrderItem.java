package com.hhplus.ecommerce.domain;

import lombok.*;

import java.time.LocalDateTime;

/**
 * OrderItem 도메인 엔티티
 * 주문의 라인 항목
 * product_name, option_name은 주문 시점의 스냅샷 저장 (감사 추적용)
 * subtotal은 unit_price * quantity로 계산되는 계산 필드
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    private Long orderItemId;
    private Long orderId;
    private Long productId;
    private Long optionId;
    private String productName;
    private String optionName;
    private Integer quantity;
    private Long unitPrice;
    private Long subtotal;
    private LocalDateTime createdAt;
}