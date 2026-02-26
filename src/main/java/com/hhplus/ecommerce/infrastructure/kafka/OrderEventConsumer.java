package com.hhplus.ecommerce.infrastructure.kafka;

import com.hhplus.ecommerce.domain.order.DataPlatformEvent;
import com.hhplus.ecommerce.domain.order.DataPlatformEventRepository;
import com.hhplus.ecommerce.domain.order.event.OrderCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * OrderEventConsumer - Kafka 주문 이벤트 소비 서비스
 *
 * 역할:
 * - order.events 토픽에서 주문 완료 이벤트 수신
 * - 수신한 이벤트를 처리 (로깅, 데이터 웨어하우스 전송, 알림 등)
 * - 수동 커밋으로 at-least-once 보장
 *
 * Consumer Group:
 * - group-id: ecommerce-order-consumer-group
 * - 동일 그룹 내 Consumer들이 파티션을 분담하여 처리
 * - 예: 3개 파티션, 3개 Consumer → 각 Consumer가 1개 파티션 담당
 *
 * Offset 관리:
 * - enable-auto-commit=false: 수동 커밋
 * - 메시지 처리 성공 후 acknowledgment.acknowledge() 호출
 * - 처리 실패 시 커밋하지 않음 → 재처리 보장 (at-least-once)
 *
 * 동시성:
 * - concurrency=3: 3개의 Consumer 스레드로 병렬 처리
 * - 각 스레드가 독립적으로 메시지 처리
 *
 * 처리 로직:
 * 1. Kafka에서 메시지 수신
 * 2. OrderCompletedEvent로 역직렬화
 * 3. 비즈니스 로직 처리 (데이터 웨어하우스 전송, 알림 등)
 * 4. 성공 시 acknowledgment.acknowledge() → Offset 커밋
 * 5. 실패 시 로깅 + 커밋하지 않음 → 다음 poll()에서 재처리
 */
@Service
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final DataPlatformEventRepository dataPlatformEventRepository;

    public OrderEventConsumer(DataPlatformEventRepository dataPlatformEventRepository) {
        this.dataPlatformEventRepository = dataPlatformEventRepository;
    }

    /**
     * 주문 완료 이벤트 리스너
     *
     * @KafkaListener 설정:
     * - topics: order.events (application.yml의 kafka.topics.order-events)
     * - groupId: ecommerce-order-consumer-group
     * - containerFactory: kafkaListenerContainerFactory (KafkaConfig 참조)
     *
     * 파라미터:
     * - @Payload: 메시지 본문 (OrderCompletedEvent)
     * - @Header(PARTITION): 메시지가 속한 파티션 번호
     * - @Header(OFFSET): 메시지 Offset
     * - acknowledgment: 수동 커밋용 객체
     *
     * 처리 흐름 (중복 처리 방지 적용):
     * 1. Kafka에서 메시지 poll
     * 2. JSON → OrderCompletedEvent 역직렬화
     * 3. handleOrderCompleted() 호출 (멱등성 보장)
     *    - DataPlatformEvent 저장 시도 (UNIQUE constraint)
     *    - 중복 시 DataIntegrityViolationException 발생 → 중복으로 판단
     * 4. 처리 성공 → acknowledgment.acknowledge()
     * 5. 중복 메시지 → acknowledgment.acknowledge() (재처리 불필요)
     * 6. 실제 실패 → 커밋하지 않음 → 재처리
     *
     * 중복 처리 방지 전략 (다층 방어):
     * - Layer 1: Producer 멱등성 (enable.idempotence=true)
     * - Layer 2: at-least-once (수동 커밋)
     * - Layer 3: DB Unique Constraint (order_id, event_type) ← 이 레이어
     *
     * @param event 주문 완료 이벤트
     * @param partition 파티션 번호
     * @param offset Offset
     * @param acknowledgment 수동 커밋 객체
     */
    @KafkaListener(
        topics = "${spring.kafka.topics.order-events}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void listen(
            @Payload OrderCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("[OrderEventConsumer] Kafka 메시지 수신 - " +
                "partition={}, offset={}, orderId={}, userId={}, amount={}",
                partition, offset, event.getOrderId(), event.getUserId(), event.getTotalAmount());

        try {
            // 비즈니스 로직 처리 (멱등성 보장)
            handleOrderCompleted(event);

            // 처리 성공 → Offset 커밋
            acknowledgment.acknowledge();
            log.info("[OrderEventConsumer] Offset 커밋 완료 - partition={}, offset={}, orderId={}",
                    partition, offset, event.getOrderId());

        } catch (DataIntegrityViolationException e) {
            // ===== 중복 메시지 처리 =====
            // UNIQUE constraint 위반 → 이미 처리된 메시지
            // at-least-once 보장으로 인한 재처리 시나리오:
            // 1. Consumer 재시작 후 미커밋 Offset 재처리
            // 2. Kafka Rebalancing 중 중복 수신
            // 3. 네트워크 재전송 (Producer 멱등성으로 방지되지만 방어적 처리)
            log.warn("[OrderEventConsumer] 중복 메시지 감지 (이미 처리됨) - " +
                    "partition={}, offset={}, orderId={}, 처리: Offset 커밋하고 skip",
                    partition, offset, event.getOrderId());

            // Offset 커밋 (중복이지만 재처리 불필요)
            acknowledgment.acknowledge();
            log.info("[OrderEventConsumer] 중복 메시지 Offset 커밋 완료 - partition={}, offset={}, orderId={}",
                    partition, offset, event.getOrderId());

        } catch (Exception e) {
            // ===== 실제 에러 처리 =====
            // 처리 실패 → 커밋하지 않음 → 다음 poll()에서 재처리
            // 예: 외부 API 타임아웃, DB 연결 실패, 네트워크 오류 등
            log.error("[OrderEventConsumer] 메시지 처리 실패 (재처리 예정) - " +
                    "partition={}, offset={}, orderId={}, error={}",
                    partition, offset, event.getOrderId(), e.getMessage(), e);

            // Offset 커밋하지 않음 → 다음 poll()에서 같은 메시지 재처리
            // 옵션:
            // - 현재: 예외 전파하지 않고 로깅만 (재처리 보장)
            // - 대안 1: 예외 전파하여 Spring Kafka ErrorHandler로 위임
            // - 대안 2: 재시도 횟수 초과 시 DLQ (Dead Letter Queue)로 전송
        }
    }

    /**
     * 주문 완료 이벤트 처리 로직 (멱등성 보장)
     *
     * 처리 순서:
     * 1. 외부 데이터 플랫폼으로 전송 (시뮬레이션)
     * 2. DataPlatformEvent 저장 (멱등성 보장)
     *    - UNIQUE constraint (order_id, event_type)로 중복 INSERT 방지
     *    - 중복 시 DataIntegrityViolationException 발생 → 상위에서 catch
     *
     * 중복 처리 시나리오:
     * 1. Consumer 재시작 후 미커밋 Offset 재처리
     *    - 이전 처리가 성공했지만 Offset 커밋 전 서버 종료
     *    - 재시작 후 같은 메시지 재처리
     *    - DataPlatformEvent INSERT 시도 → UNIQUE constraint 위반
     *    - DataIntegrityViolationException → 중복으로 판단 → Offset 커밋
     *
     * 2. Kafka Rebalancing 중 중복 수신
     *    - Consumer Group 내 Consumer 추가/제거 시 Rebalancing
     *    - 일시적으로 같은 메시지를 여러 Consumer가 처리
     *    - 첫 처리: 성공 → DataPlatformEvent 저장
     *    - 중복 처리: UNIQUE constraint 위반 → skip
     *
     * 실제 구현 예시 (TODO):
     * 1. 데이터 웨어하우스로 전송
     *    - HTTP API 호출 (재시도 로직 포함)
     * 2. 알림 발송
     *    - 주문 완료 SMS/Email 발송
     * 3. 분석 시스템 연동
     *    - 실시간 대시보드 업데이트
     *
     * @param event 주문 완료 이벤트
     * @throws Exception 처리 실패 시 (외부 API 오류, 네트워크 오류 등)
     * @throws DataIntegrityViolationException 중복 메시지 (UNIQUE constraint 위반)
     */
    private void handleOrderCompleted(OrderCompletedEvent event) throws Exception {
        log.info("[OrderEventConsumer] >>> 주문 완료 이벤트 처리 시작 <<<");
        log.info("[OrderEventConsumer]     - orderId: {}", event.getOrderId());
        log.info("[OrderEventConsumer]     - userId: {}", event.getUserId());
        log.info("[OrderEventConsumer]     - totalAmount: {}", event.getTotalAmount());
        log.info("[OrderEventConsumer]     - occurredAt: {}", event.getOccurredAt());

        // ===== 1. 외부 데이터 플랫폼으로 전송 (시뮬레이션) =====
        // 실제 구현:
        // dataWarehouseClient.sendOrder(OrderDto.from(event));
        // notificationService.sendOrderCompletedNotification(event.getUserId(), event.getOrderId());
        // analyticsService.trackOrderCompleted(event);

        // 시뮬레이션: 처리 시간 (외부 API 호출 시간)
        Thread.sleep(100);

        log.info("[OrderEventConsumer] 외부 데이터 플랫폼 전송 완료 (시뮬레이션)");

        // ===== 2. DataPlatformEvent 저장 (멱등성 보장) =====
        // UNIQUE constraint (order_id, event_type)로 중복 INSERT 방지
        // 중복 시 DataIntegrityViolationException 발생 → 상위 catch 블록에서 처리
        DataPlatformEvent dataPlatformEvent = DataPlatformEvent.create(
                event.getOrderId(),
                "ORDER_COMPLETED",  // event_type
                event.getUserId(),
                event.getTotalAmount(),
                event.getOccurredAt()
        );

        dataPlatformEventRepository.save(dataPlatformEvent);
        log.info("[OrderEventConsumer] DataPlatformEvent 저장 완료 - eventId={}, orderId={}",
                dataPlatformEvent.getEventId(), event.getOrderId());

        log.info("[OrderEventConsumer] >>> 주문 완료 이벤트 처리 완료 <<<");

        // 실패 테스트용 시뮬레이션 (선택적):
        // if (event.getOrderId() % 10 == 0) {
        //     throw new Exception("Data warehouse connection timeout");
        // }
    }
}