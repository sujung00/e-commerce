package com.hhplus.ecommerce.application.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * CreateOrderRequestDto - Application 레이어 전용 주문 생성 DTO
 *
 * 역할:
 * - Presentation 레이어의 CreateOrderCommand를 Application 레이어로 변환
 * - Presentation 의존성 제거 (계층 간 완전 분리)
 * - OrderService 내부에서만 사용
 *
 * 구조:
 * Presentation (CreateOrderCommand)
 *     ↓ Mapper 변환
 * Application (CreateOrderRequestDto) ← 이 클래스
 *     ↓
 * Domain (Order, OrderItem)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequestDto {
    private Long userId;
    private Long couponId;
    private List<OrderItemDto> items;

    /**
     * OrderItemDto - Application 레이어 전용 주문 항목 DTO
     *
     * 역할:
     * - Presentation의 OrderItemRequest를 Application 레이어로 변환
     * - Domain의 OrderItem 생성 시 필요한 정보 제공
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDto {
        private Long productId;
        private Long optionId;
        private Integer quantity;
    }
}
