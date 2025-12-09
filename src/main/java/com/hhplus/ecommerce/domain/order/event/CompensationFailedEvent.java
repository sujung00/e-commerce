package com.hhplus.ecommerce.domain.order.event;

import lombok.Getter;
import lombok.ToString;

/**
 * 보상 트랜잭션 실패 이벤트
 *
 * 용도: 보상 처리 실패 시 알림을 트랜잭션 외부에서 비동기 처리
 * 발행 시점: OrderSagaService.compensateOrder() 예외 발생 시
 * 리스너: OrderAlertEventListener (비동기)
 */
@Getter
@ToString
public class CompensationFailedEvent {

    private final Long orderId;
    private final Long userId;
    private final String errorMessage;

    public CompensationFailedEvent(Long orderId, Long userId, String errorMessage) {
        this.orderId = orderId;
        this.userId = userId;
        this.errorMessage = errorMessage;
    }
}