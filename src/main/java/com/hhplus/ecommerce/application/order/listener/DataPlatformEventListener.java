package com.hhplus.ecommerce.application.order.listener;

import com.hhplus.ecommerce.domain.order.event.OrderCompletedEvent;
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
 * - 주문 완료 시점에 실시간으로 주문 정보를 데이터 플랫폼으로 전송
 * - 트랜잭션 커밋 후 비동기로 실행하여 트랜잭션과 관심사 분리
 * - 전송 실패 시에도 주문 트랜잭션에 영향 없음
 *
 * 트랜잭션 분리 이유:
 * - 데이터 플랫폼 전송은 외부 I/O 작업 (HTTP, Kafka 등)
 * - 트랜잭션 내부에서 실행 시 성능 저하 및 트랜잭션 지연
 * - 전송 실패가 비즈니스 트랜잭션에 영향을 주지 않아야 함
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
 * - 실시간 전송 실패 시 Outbox 배치가 재전송
 */
@Component
public class DataPlatformEventListener {

    private static final Logger log = LoggerFactory.getLogger(DataPlatformEventListener.class);

    // TODO: 실제 구현 시 DataPlatformClient 주입
    // private final DataPlatformClient dataPlatformClient;

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
     * 데이터 플랫폼으로 주문 정보 전송
     *
     * 전송 방법 (실제 구현 시 선택):
     * 1. HTTP 방식:
     *    - RestTemplate 또는 WebClient 사용
     *    - POST http://data-platform.example.com/api/orders
     *
     * 2. Kafka 방식:
     *    - KafkaTemplate 사용
     *    - kafkaTemplate.send("order.completed", event).get();
     *
     * 3. 메시지 큐 방식:
     *    - RabbitMQ, AWS SQS 등
     *
     * @param event 주문 완료 이벤트
     * @throws Exception 전송 실패 시
     */
    private void sendToDataPlatform(OrderCompletedEvent event) throws Exception {
        // TODO: 실제 구현
        // 방법 1: HTTP 전송
        // OrderPlatformDto dto = new OrderPlatformDto(
        //     event.getOrderId(),
        //     event.getUserId(),
        //     event.getTotalAmount(),
        //     event.getOccurredAt()
        // );
        // dataPlatformClient.sendOrder(dto);
        //
        // 방법 2: Kafka 전송
        // kafkaTemplate.send("order.completed",
        //                   String.valueOf(event.getOrderId()),
        //                   event).get();

        // 현재: 로깅만 수행 (프로토타입)
        log.info("[DataPlatformEventListener] >>> 데이터 플랫폼으로 전송 <<<");
        log.info("[DataPlatformEventListener]     - orderId: {}", event.getOrderId());
        log.info("[DataPlatformEventListener]     - userId: {}", event.getUserId());
        log.info("[DataPlatformEventListener]     - totalAmount: {}", event.getTotalAmount());
        log.info("[DataPlatformEventListener]     - occurredAt: {}", event.getOccurredAt());
        log.info("[DataPlatformEventListener] >>> 전송 완료 (시뮬레이션) <<<");

        // 실패 테스트용 시뮬레이션:
        // throw new Exception("Data platform connection timeout");
    }
}