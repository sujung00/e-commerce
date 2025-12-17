package com.hhplus.ecommerce.domain.order.event;

import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 결제 완료 이벤트
 * 결제가 성공적으로 완료되었을 때 발행되는 도메인 이벤트
 */
@Getter
@ToString
public class PaymentCompletedEvent extends ApplicationEvent {

    private final Long orderId;
    private final Long userId;
    private final String paymentMethod;
    private final Long paymentAmount;
    private final LocalDateTime occurredAt;

    /**
     * 결제 완료 이벤트 생성 (현재 시간 자동 설정)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param paymentMethod 결제 수단
     * @param paymentAmount 결제 금액
     */
    public PaymentCompletedEvent(Long orderId, Long userId, String paymentMethod, Long paymentAmount) {
        super(orderId);
        this.orderId = orderId;
        this.userId = userId;
        this.paymentMethod = paymentMethod;
        this.paymentAmount = paymentAmount;
        this.occurredAt = LocalDateTime.now();
    }

    /**
     * 결제 완료 이벤트 생성 (테스트용 - 명시적 시간 설정)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param paymentMethod 결제 수단
     * @param paymentAmount 결제 금액
     * @param occurredAt 이벤트 발생 시간
     */
    public PaymentCompletedEvent(Long orderId, Long userId, String paymentMethod, Long paymentAmount, LocalDateTime occurredAt) {
        super(orderId);
        this.orderId = orderId;
        this.userId = userId;
        this.paymentMethod = paymentMethod;
        this.paymentAmount = paymentAmount;
        this.occurredAt = occurredAt;
    }
}
