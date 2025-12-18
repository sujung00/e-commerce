package com.hhplus.ecommerce.application.order.listener;

import com.hhplus.ecommerce.domain.order.event.OrderCompletedEvent;
import com.hhplus.ecommerce.infrastructure.kafka.OrderEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * DataPlatformEventListener - 데이터 플랫폼 실시간 전송 이벤트 리스너
 *
 * 역할:
 * - 주문 완료 시점에 실시간으로 주문 정보를 Kafka로 발행
 * - 트랜잭션 커밋 후 비동기로 실행하여 트랜잭션과 관심사 분리
 * - Kafka 전송 실패 시에도 주문 트랜잭션에 영향 없음
 *
 * 트랜잭션 분리 이유:
 * - Kafka 메시지 발행은 외부 I/O 작업 (네트워크 통신)
 * - 트랜잭션 내부에서 실행 시 성능 저하 및 트랜잭션 지연
 * - Kafka 전송 실패가 비즈니스 트랜잭션에 영향을 주지 않아야 함
 *
 * 이벤트 처리 시점: AFTER_COMMIT
 * - 트랜잭션 커밋 성공 후에만 실행
 * - 주문 생성 실패(롤백) 시 이벤트 미발행으로 데이터 정합성 보장
 *
 * 비동기 처리:
 * - @Async로 별도 스레드에서 실행
 * - 메인 주문 트랜잭션 블로킹 방지
 * - 실시간 응답성 향상
 *
 * 백업 메커니즘:
 * - Outbox 패턴과 병행 (배치 기반 재전송 보장)
 * - Kafka 전송 실패 시 Outbox 배치가 재전송
 *
 * Kafka 전송:
 * - Topic: order.events
 * - Key: orderId (파티션 분배 기준)
 * - Value: OrderCompletedEvent (JSON)
 */
@Component
public class DataPlatformEventListener {

    private static final Logger log = LoggerFactory.getLogger(DataPlatformEventListener.class);

    private final OrderEventProducer orderEventProducer;

    public DataPlatformEventListener(OrderEventProducer orderEventProducer) {
        this.orderEventProducer = orderEventProducer;
    }

    /**
     * 주문 완료 이벤트 리스너
     *
     * 트랜잭션 커밋 후 비동기로 데이터 플랫폼 전송
     * - 주문 정보: orderId, userId, totalAmount, timestamp
     * - 전송 방식: HTTP POST 또는 Kafka 메시지 발행
     *
     * 실패 처리:
     * - 전송 실패는 로깅만 하고 예외를 전파하지 않음
     * - 비즈니스 트랜잭션은 이미 성공했으므로 전송은 선택적 기능
     * - Outbox 배치가 재전송 보장
     *
     * @param event 주문 완료 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        log.info("[DataPlatformEventListener] 주문 완료 이벤트 수신 (실시간 전송) - orderId={}, userId={}, amount={}",
                event.getOrderId(), event.getUserId(), event.getTotalAmount());

        try {
            // ===== 실시간 데이터 플랫폼 전송 =====
            sendToDataPlatform(event);

            log.info("[DataPlatformEventListener] 데이터 플랫폼 전송 성공 - orderId={}", event.getOrderId());

        } catch (Exception e) {
            // 전송 실패는 로깅만 하고 예외를 전파하지 않음
            // 비즈니스 트랜잭션은 이미 성공했으므로 전송은 선택적 기능
            // Outbox 배치가 재전송 보장
            log.error("[DataPlatformEventListener] 데이터 플랫폼 전송 실패 (Outbox 배치가 재시도) - orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * 데이터 플랫폼으로 주문 정보 전송 (Kafka 기반)
     *
     * Kafka 전송:
     * - OrderEventProducer를 통해 order.events 토픽으로 발행
     * - Key: orderId (파티션 분배 기준)
     * - Value: OrderCompletedEvent (JSON 직렬화)
     *
     * 비동기 전송:
     * - KafkaTemplate.send()는 CompletableFuture 반환
     * - 즉시 반환되며 별도 스레드에서 전송 수행
     * - 콜백으로 성공/실패 처리 (OrderEventProducer 내부)
     *
     * 실패 처리:
     * - Kafka 전송 실패 시 OrderEventProducer에서 에러 로깅
     * - Outbox 테이블에 이미 저장되어 있으므로 배치 재전송 보장
     *
     * @param event 주문 완료 이벤트
     * @throws Exception 전송 실패 시 (재시도는 KafkaConfig의 retries 설정)
     */
    private void sendToDataPlatform(OrderCompletedEvent event) throws Exception {
        log.info("[DataPlatformEventListener] >>> Kafka로 주문 이벤트 발행 <<<");
        log.info("[DataPlatformEventListener]     - orderId: {}", event.getOrderId());
        log.info("[DataPlatformEventListener]     - userId: {}", event.getUserId());
        log.info("[DataPlatformEventListener]     - totalAmount: {}", event.getTotalAmount());
        log.info("[DataPlatformEventListener]     - occurredAt: {}", event.getOccurredAt());

        // Kafka 메시지 발행 (비동기)
        orderEventProducer.publishOrderCompletedEvent(event);

        log.info("[DataPlatformEventListener] >>> Kafka 발행 완료 (비동기 전송 중) <<<");
    }
}