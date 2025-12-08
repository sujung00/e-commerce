package com.hhplus.ecommerce.domain.order.event;

import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 주문 취소 도메인 이벤트
 *
 * 책임:
 * - 주문 취소 트랜잭션 커밋 후 외부 시스템에 취소 정보 전파
 * - 배송 취소, 결제 취소(Void), 재고 복구 등 보상 트랜잭션 트리거
 *
 * 발행 시점:
 * - OrderService.cancelOrder() 트랜잭션 커밋 직후
 *
 * 처리 방식:
 * - @TransactionalEventListener(phase = AFTER_COMMIT)로 수신
 * - @Async로 비동기 처리
 */
@Getter
@ToString
public class OrderCancelledEvent extends ApplicationEvent {

    /**
     * 주문 ID
     */
    private final Long orderId;

    /**
     * 사용자 ID
     */
    private final Long userId;

    /**
     * 취소 사유
     */
    private final String cancelReason;

    /**
     * 이벤트 발생 시간
     */
    private final LocalDateTime timestamp;

    /**
     * 주문 취소 이벤트 생성 (timestamp 자동 설정)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param cancelReason 취소 사유
     */
    public OrderCancelledEvent(Long orderId, Long userId, String cancelReason) {
        super(orderId); // ApplicationEvent의 source로 orderId 사용
        this.orderId = orderId;
        this.userId = userId;
        this.cancelReason = cancelReason;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 주문 취소 이벤트 생성 (모든 필드 지정)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param cancelReason 취소 사유
     * @param timestamp 이벤트 발생 시간 (테스트용)
     */
    public OrderCancelledEvent(Long orderId, Long userId, String cancelReason, LocalDateTime timestamp) {
        super(orderId); // ApplicationEvent의 source로 orderId 사용
        this.orderId = orderId;
        this.userId = userId;
        this.cancelReason = cancelReason;
        this.timestamp = timestamp;
    }
}
