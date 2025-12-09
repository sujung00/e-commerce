package com.hhplus.ecommerce.domain.order.event;

import lombok.Getter;
import lombok.ToString;

/**
 * 결제 성공 이벤트
 *
 * 용도: 주문 생성 및 결제 완료 후 알림을 트랜잭션 외부에서 비동기 처리
 * 발행 시점: OrderSagaService.createOrderWithPayment() 트랜잭션 성공 후
 * 리스너: OrderAlertEventListener (비동기)
 */
@Getter
@ToString
public class PaymentSuccessEvent {

    private final Long orderId;
    private final Long userId;
    private final Long finalAmount;

    public PaymentSuccessEvent(Long orderId, Long userId, Long finalAmount) {
        this.orderId = orderId;
        this.userId = userId;
        this.finalAmount = finalAmount;
    }
}