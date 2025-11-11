package com.hhplus.ecommerce.domain.order;

import lombok.*;

import java.time.LocalDateTime;

/**
 * OrderItem 도메인 엔티티
 * 주문 내 각 상품 항목 정보 (스냅샷)
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
    private String productName;      // 스냅샷: 주문 시점의 상품명
    private String optionName;       // 스냅샷: 주문 시점의 옵션명
    private Integer quantity;
    private Long unitPrice;
    private Long subtotal;           // unitPrice * quantity
    private LocalDateTime createdAt;

    /**
     * OrderItem 생성 팩토리 메서드
     */
    public static OrderItem createOrderItem(Long productId, Long optionId, String productName,
                                           String optionName, Integer quantity, Long unitPrice) {
        Long subtotal = unitPrice * quantity;
        return OrderItem.builder()
                .productId(productId)
                .optionId(optionId)
                .productName(productName)
                .optionName(optionName)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .subtotal(subtotal)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
