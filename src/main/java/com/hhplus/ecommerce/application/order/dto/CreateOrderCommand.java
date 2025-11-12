package com.hhplus.ecommerce.application.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 주문 생성 커맨드 (Application layer 내부 DTO)
 * Presentation layer의 CreateOrderRequest와 독립적
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderCommand {
    private List<OrderItemCommand> orderItems;
    private Long couponId;
}