package com.hhplus.ecommerce.infrastructure.kafka;

import com.hhplus.ecommerce.domain.order.event.OrderCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * OrderEventProducer - Kafka 주문 이벤트 발행 서비스
 *
 * 역할:
 * - 주문 완료 이벤트를 Kafka 토픽으로 발행
 * - 비동기 전송 + 콜백으로 성공/실패 처리
 * - 실패 시 로깅 및 에러 처리
 *
 * Kafka 메시지 구조:
 * - Key: orderId (String) → 같은 주문 ID는 같은 파티션으로 전송
 * - Value: OrderCompletedEvent (JSON)
 * - Topic: order.events
 *
 * 동작 방식:
 * 1. KafkaTemplate.send()로 비동기 전송
 * 2. CompletableFuture로 결과 처리
 * 3. 성공: 로그 기록
 * 4. 실패: 에러 로그 + 예외 처리 (재시도는 KafkaConfig의 retries 설정)
 *
 * 파티션 분배:
 * - Key 기반 파티셔닝: hash(orderId) % partition_count
 * - 같은 orderId는 항상 같은 파티션으로 전송 → 순서 보장
 *
 * 멱등성:
 * - enable.idempotence=true 설정으로 중복 메시지 방지
 * - Producer가 자동으로 메시지 ID 관리
 */
@Service
public class OrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);

    private final KafkaTemplate<String, OrderCompletedEvent> kafkaTemplate;
    private final String topicName;

    public OrderEventProducer(
            KafkaTemplate<String, OrderCompletedEvent> kafkaTemplate,
            @Value("${kafka.topics.order-events}") String topicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    /**
     * 주문 완료 이벤트 발행
     *
     * 비동기 전송:
     * - KafkaTemplate.send()는 CompletableFuture 반환
     * - 즉시 반환되며 별도 스레드에서 전송 수행
     * - 콜백으로 성공/실패 처리
     *
     * 파티션 할당:
     * - Key: orderId.toString()
     * - hash(orderId) % partition_count로 파티션 결정
     * - 예: orderId=101 → hash(101) % 3 = 2 → Partition 2
     *
     * 실패 처리:
     * - KafkaConfig의 retries=3 설정으로 자동 재시도
     * - 3회 실패 시 콜백의 exceptionally() 호출
     * - 에러 로깅만 하고 예외를 전파하지 않음
     *   (비즈니스 트랜잭션은 이미 성공했으므로)
     *
     * @param event 주문 완료 이벤트
     */
    public void publishOrderCompletedEvent(OrderCompletedEvent event) {
        String key = String.valueOf(event.getOrderId());

        log.info("[OrderEventProducer] Kafka 메시지 발행 시작 - topic={}, key={}, orderId={}, userId={}, amount={}",
                topicName, key, event.getOrderId(), event.getUserId(), event.getTotalAmount());

        // 비동기 전송
        CompletableFuture<SendResult<String, OrderCompletedEvent>> future =
            kafkaTemplate.send(topicName, key, event);

        // 콜백 처리
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                // 전송 성공
                var metadata = result.getRecordMetadata();
                log.info("[OrderEventProducer] Kafka 메시지 발행 성공 - " +
                        "topic={}, partition={}, offset={}, orderId={}",
                        metadata.topic(),
                        metadata.partition(),
                        metadata.offset(),
                        event.getOrderId());
            } else {
                // 전송 실패 (3회 재시도 후에도 실패)
                log.error("[OrderEventProducer] Kafka 메시지 발행 실패 - " +
                        "topic={}, key={}, orderId={}, error={}",
                        topicName, key, event.getOrderId(), ex.getMessage(), ex);

                // 실패한 메시지는 Outbox 테이블에서 배치 재전송
                // (Outbox는 이미 OrderTransactionService에서 저장됨)
            }
        });
    }
}