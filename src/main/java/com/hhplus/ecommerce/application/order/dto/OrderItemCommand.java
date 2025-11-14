package com.hhplus.ecommerce.application.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 항목 커맨드 (Application layer 내부 DTO)
 * Presentation layer의 OrderItemRequest와 독립적
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemCommand {
    private Long productId;
    private Long optionId;
    private Integer quantity;
}
