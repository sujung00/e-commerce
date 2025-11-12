package com.hhplus.ecommerce.domain.order;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Order 도메인 엔티티 (Rich Domain Model)
 *
 * 책임:
 * - 주문의 상태 관리 및 상태 전환 로직
 * - 주문 금액 계산 및 유효성 검증
 * - 주문 항목 관리
 *
 * 핵심 비즈니스 규칙:
 * - 주문 상태는 PENDING → COMPLETED → CANCELLED 순서로만 변경 가능
 * - 취소된 주문은 다시 활성화 불가능
 * - 주문 생성 시 반드시 최소 1개 이상의 항목 필요
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private Long orderId;
    private Long userId;
    private OrderStatus orderStatus;  // 도메인 값 객체로 변경
    private Long couponId;
    private Long couponDiscount;
    private Long subtotal;
    private Long finalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime cancelledAt;

    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    /**
     * 주문 생성 팩토리 메서드 (정적 팩토리)
     *
     * 비즈니스 규칙:
     * - 주문은 항상 COMPLETED 상태로 생성
     * - 쿠폰 할인은 0 이상이어야 함
     * - 최종 금액은 0 이상이어야 함
     */
    public static Order createOrder(Long userId, Long couponId, Long couponDiscount, Long subtotal, Long finalAmount) {
        if (finalAmount < 0) {
            throw new IllegalArgumentException("최종 금액은 음수가 될 수 없습니다");
        }
        if (couponDiscount < 0) {
            throw new IllegalArgumentException("쿠폰 할인액은 음수가 될 수 없습니다");
        }

        return Order.builder()
                .userId(userId)
                .couponId(couponId)
                .couponDiscount(couponDiscount)
                .subtotal(subtotal)
                .finalAmount(finalAmount)
                .orderStatus(OrderStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 주문 항목 추가
     *
     * 비즈니스 규칙:
     * - null이 아닌 유효한 OrderItem만 추가
     */
    public void addOrderItem(OrderItem orderItem) {
        if (orderItem == null) {
            throw new IllegalArgumentException("null 주문 항목을 추가할 수 없습니다");
        }
        this.orderItems.add(orderItem);
    }

    /**
     * 주문 항목 개수
     */
    public int getOrderItemCount() {
        return this.orderItems.size();
    }

    /**
     * 총 항목 수량 계산
     */
    public Integer getTotalQuantity() {
        return this.orderItems.stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();
    }

    /**
     * 상태 전환: COMPLETED → CANCELLED
     *
     * 비즈니스 규칙:
     * - COMPLETED 상태인 주문만 취소 가능
     * - 취소 시 현재 시간을 기록
     *
     * @throws InvalidOrderStatusException 주문이 취소 가능한 상태가 아님
     */
    public void cancel() {
        if (this.orderStatus != OrderStatus.COMPLETED) {
            throw new InvalidOrderStatusException(this.orderId, this.orderStatus.name());
        }
        this.orderStatus = OrderStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문이 취소 가능한 상태인지 확인
     */
    public boolean isCancellable() {
        return this.orderStatus == OrderStatus.COMPLETED;
    }

    /**
     * 주문이 이미 취소된 상태인지 확인
     */
    public boolean isCancelled() {
        return this.orderStatus == OrderStatus.CANCELLED;
    }

    /**
     * 주문이 완료 상태인지 확인
     */
    public boolean isCompleted() {
        return this.orderStatus == OrderStatus.COMPLETED;
    }

    /**
     * 쿠폰이 적용된 주문인지 확인
     */
    public boolean hasCoupon() {
        return this.couponId != null;
    }

    /**
     * 실제 결제액 (쿠폰 할인 후 최종 금액)
     */
    public Long getPaymentAmount() {
        return this.finalAmount;
    }

    /**
     * 쿠폰을 제외한 원가 합계
     */
    public Long getBaseAmount() {
        return this.subtotal;
    }

    /**
     * 총 할인액 (쿠폰 할인)
     */
    public Long getTotalDiscount() {
        return this.couponDiscount;
    }

    /**
     * 할인율 계산 (%)
     * 예: 10000 - 5000 = 5000이면 50%
     */
    public double getDiscountPercentage() {
        if (this.subtotal == 0) {
            return 0;
        }
        return (double) this.couponDiscount / this.subtotal * 100;
    }
}
