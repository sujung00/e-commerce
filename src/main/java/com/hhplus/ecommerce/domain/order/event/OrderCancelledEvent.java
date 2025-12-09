package com.hhplus.ecommerce.domain.order.event;

import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 주문 취소 이벤트
 * 주문이 취소되었을 때 발행되는 도메인 이벤트
 */
@Getter
@ToString
public class OrderCancelledEvent extends ApplicationEvent {

    private final Long orderId;
    private final Long userId;
    private final String cancelReason;
    private final LocalDateTime occurredAt;

    /**
     * 주문 취소 이벤트 생성 (현재 시간 자동 설정)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param cancelReason 취소 사유
     */
    public OrderCancelledEvent(Long orderId, Long userId, String cancelReason) {
        super(orderId);
        this.orderId = orderId;
        this.userId = userId;
        this.cancelReason = cancelReason;
        this.occurredAt = LocalDateTime.now();
    }

    /**
     * 주문 취소 이벤트 생성 (테스트용 - 명시적 시간 설정)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param cancelReason 취소 사유
     * @param occurredAt 이벤트 발생 시간
     */
    public OrderCancelledEvent(Long orderId, Long userId, String cancelReason, LocalDateTime occurredAt) {
        super(orderId);
        this.orderId = orderId;
        this.userId = userId;
        this.cancelReason = cancelReason;
        this.occurredAt = occurredAt;
    }
}
