package com.hhplus.ecommerce.domain.order.event;

import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 주문 완료 도메인 이벤트
 *
 * 책임:
 * - 주문 트랜잭션 커밋 후 외부 시스템에 주문 정보 전파
 * - 배송 시스템, 데이터 플랫폼, 알림 시스템 등에 주문 완료 알림
 *
 * 발행 시점:
 * - OrderTransactionService.executeTransactionalOrder() 트랜잭션 커밋 직후
 *
 * 처리 방식:
 * - @TransactionalEventListener(phase = AFTER_COMMIT)로 수신
 * - @Async로 비동기 처리
 */
@Getter
@ToString
public class OrderCompletedEvent extends ApplicationEvent {

    /**
     * 주문 ID
     */
    private final Long orderId;

    /**
     * 사용자 ID
     */
    private final Long userId;

    /**
     * 주문 총 금액 (쿠폰 할인 적용 후)
     */
    private final Long totalAmount;

    /**
     * 이벤트 발생 시간
     */
    private final LocalDateTime timestamp;

    /**
     * 주문 완료 이벤트 생성 (timestamp 자동 설정)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param totalAmount 주문 총 금액
     */
    public OrderCompletedEvent(Long orderId, Long userId, Long totalAmount) {
        super(orderId); // ApplicationEvent의 source로 orderId 사용
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 주문 완료 이벤트 생성 (모든 필드 지정)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param totalAmount 주문 총 금액
     * @param timestamp 이벤트 발생 시간 (테스트용)
     */
    public OrderCompletedEvent(Long orderId, Long userId, Long totalAmount, LocalDateTime timestamp) {
        super(orderId); // ApplicationEvent의 source로 orderId 사용
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.timestamp = timestamp;
    }
}
