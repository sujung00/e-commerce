package com.hhplus.ecommerce.domain.order.event;

import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 주문 생성 완료 이벤트
 *
 * 용도: 주문 Core Transaction 완료 후 부가 작업(쿠폰 사용, 상품 상태 업데이트)을 이벤트 기반으로 처리
 * 발행 시점: OrderTransactionService.executeTransactionalOrder() 트랜잭션 내부
 * 리스너:
 *   1. CouponEventHandler (동기, BEFORE_COMMIT) - 쿠폰 사용 처리
 *   2. ProductStatusEventHandler (비동기, AFTER_COMMIT) - 상품 상태 업데이트
 *
 * Phase 2 개선:
 * - God Transaction 해체
 * - Core TX와 비핵심 후처리 로직 분리
 * - 재고 차감은 Core TX 내부 유지
 */
@Getter
@ToString
public class OrderCreatedEvent {

    private final Long orderId;
    private final Long userId;
    private final Long couponId;  // nullable
    private final List<OrderItemInfo> orderItems;
    private final Set<Long> productIds;

    public OrderCreatedEvent(Long orderId,
                             Long userId,
                             Long couponId,
                             List<OrderItemInfo> orderItems) {
        this.orderId = orderId;
        this.userId = userId;
        this.couponId = couponId;
        this.orderItems = orderItems;
        this.productIds = orderItems.stream()
                .map(OrderItemInfo::getProductId)
                .collect(Collectors.toSet());
    }

    /**
     * 주문 항목 정보 (최소한의 정보만 포함)
     */
    @Getter
    @ToString
    public static class OrderItemInfo {
        private final Long productId;
        private final Long optionId;
        private final Integer quantity;

        public OrderItemInfo(Long productId, Long optionId, Integer quantity) {
            this.productId = productId;
            this.optionId = optionId;
            this.quantity = quantity;
        }
    }
}