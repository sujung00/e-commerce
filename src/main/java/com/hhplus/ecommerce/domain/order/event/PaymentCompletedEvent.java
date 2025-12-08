package com.hhplus.ecommerce.domain.order.event;

import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 결제 완료 도메인 이벤트
 *
 * 책임:
 * - 결제 완료 후 외부 시스템에 결제 정보 전파
 * - 회계 시스템 연동, 결제 이력 저장, 정산 처리 트리거
 *
 * 발행 시점:
 * - OrderSagaService.processPayment() 트랜잭션 커밋 직후
 *
 * 처리 방식:
 * - @TransactionalEventListener(phase = AFTER_COMMIT)로 수신
 * - @Async로 비동기 처리
 */
@Getter
@ToString
public class PaymentCompletedEvent extends ApplicationEvent {

    /**
     * 주문 ID
     */
    private final Long orderId;

    /**
     * 사용자 ID
     */
    private final Long userId;

    /**
     * 결제 수단 (예: POINT, CARD, BANK_TRANSFER)
     */
    private final String paymentMethod;

    /**
     * 결제 금액
     */
    private final Long paymentAmount;

    /**
     * 이벤트 발생 시간
     */
    private final LocalDateTime timestamp;

    /**
     * 결제 완료 이벤트 생성 (timestamp 자동 설정)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param paymentMethod 결제 수단
     * @param paymentAmount 결제 금액
     */
    public PaymentCompletedEvent(Long orderId, Long userId, String paymentMethod, Long paymentAmount) {
        super(orderId); // ApplicationEvent의 source로 orderId 사용
        this.orderId = orderId;
        this.userId = userId;
        this.paymentMethod = paymentMethod;
        this.paymentAmount = paymentAmount;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 결제 완료 이벤트 생성 (모든 필드 지정)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param paymentMethod 결제 수단
     * @param paymentAmount 결제 금액
     * @param timestamp 이벤트 발생 시간 (테스트용)
     */
    public PaymentCompletedEvent(Long orderId, Long userId, String paymentMethod, Long paymentAmount, LocalDateTime timestamp) {
        super(orderId); // ApplicationEvent의 source로 orderId 사용
        this.orderId = orderId;
        this.userId = userId;
        this.paymentMethod = paymentMethod;
        this.paymentAmount = paymentAmount;
        this.timestamp = timestamp;
    }
}
