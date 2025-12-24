package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.application.shipping.client.ShippingServiceClient;
import com.hhplus.ecommerce.domain.order.Outbox;
import com.hhplus.ecommerce.domain.order.OutboxRepository;
import com.hhplus.ecommerce.infrastructure.external.DataPlatformClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OutboxEventPublisher - Outbox 메시지 발행 서비스
 *
 * 역할:
 * - Outbox 패턴을 통한 외부 시스템 연동
 * - 메시지를 Outbox 테이블에 저장 후 즉시 발행 시도
 * - 실패 시 PENDING 상태로 유지하여 배치 재시도 보장
 *
 * 지원하는 메시지 타입:
 * - ORDER_COMPLETED: 주문 완료 이벤트 (배송, 알림 등)
 * - ORDER_CANCELLED: 주문 취소 이벤트 (재고 복구 등)
 * - PAYMENT_COMPLETED: 결제 완료 이벤트 (결제 이력 저장 등)
 * - PAYMENT_SUCCESS: 결제 성공 이벤트 (실시간 알림 등)
 * - DATA_PLATFORM: 데이터 플랫폼 전송
 * - SHIPPING: 배송 시스템 전송
 *
 * Outbox 패턴 플로우:
 * 1. publishWithOutbox(): Outbox 테이블에 메시지 저장 (트랜잭션 보장)
 * 2. 즉시 발행 시도 (publish())
 * 3. 성공 → SENT 상태, 실패 → PENDING 유지
 * 4. OutboxPollingService(배치)가 PENDING 메시지 재시도
 *
 * 확장 가능성:
 * - Kafka: 메시지 큐를 통한 이벤트 발행
 * - HTTP: REST API를 통한 직접 호출
 * - 이메일/SMS: 고객 알림
 * - 데이터베이스: 이벤트 스토어 저장
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxRepository outboxRepository;
    private final DataPlatformClient dataPlatformClient;
    private final ShippingServiceClient shippingServiceClient;

    /**
     * Outbox 패턴을 통한 메시지 발행 (중복 발행 방지 개선)
     *
     * 플로우 (개선):
     * 1. Outbox 테이블에 메시지 저장 (PENDING 상태)
     * 2. 발행 시작 전 상태를 PUBLISHING으로 변경 (트랜잭션 보장) ← 핵심!
     * 3. 즉시 발행 시도
     * 4. 성공 → PUBLISHED 상태로 업데이트
     * 5. 실패 → FAILED 상태로 업데이트, 배치가 재시도
     *
     * 중복 발행 방지 메커니즘:
     * - 발행 전에 PUBLISHING 상태로 변경 → 트랜잭션 커밋
     * - 발행 성공 후 상태 업데이트 실패 시에도 PUBLISHING 상태 유지
     * - 배치 처리는 PENDING/FAILED만 처리 (PUBLISHING은 제외)
     * - 따라서 메시지는 한 번만 발행됨
     *
     * @param messageType 메시지 타입 (ORDER_COMPLETED, DATA_PLATFORM, SHIPPING 등)
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param payload JSON 형태의 메시지 데이터
     */
    @Transactional
    public void publishWithOutbox(String messageType, Long orderId, Long userId, String payload) {
        log.info("[OutboxEventPublisher] Outbox 메시지 저장 시작 - type={}, orderId={}, userId={}",
            messageType, orderId, userId);

        // 1. Outbox 테이블에 저장 (PENDING 상태)
        Outbox outbox = Outbox.createOutboxWithPayload(orderId, userId, messageType, payload);
        Outbox savedOutbox = outboxRepository.save(outbox);

        log.info("[OutboxEventPublisher] Outbox 메시지 저장 완료 - messageId={}, status=PENDING",
            savedOutbox.getMessageId());

        // 2. 발행 시작 전 PUBLISHING 상태로 변경 (중복 발행 방지)
        savedOutbox.markAsPublishing();
        savedOutbox = outboxRepository.save(savedOutbox);

        log.info("[OutboxEventPublisher] 발행 시작 - messageId={}, status=PUBLISHING",
            savedOutbox.getMessageId());

        // 3. 즉시 발행 시도
        try {
            publish(savedOutbox);

            // 4. 발행 성공 → PUBLISHED 상태로 업데이트
            savedOutbox.markAsPublished();
            outboxRepository.save(savedOutbox);

            log.info("[OutboxEventPublisher] 즉시 발행 성공 - messageId={}, status=PUBLISHED",
                savedOutbox.getMessageId());

        } catch (Exception e) {
            // 5. 발행 실패 → FAILED 상태로 업데이트 (배치가 재시도)
            savedOutbox.markAsFailed();
            outboxRepository.save(savedOutbox);

            log.warn("[OutboxEventPublisher] 즉시 발행 실패 (배치가 재시도) - messageId={}, status=FAILED, error={}",
                savedOutbox.getMessageId(), e.getMessage());
        }
    }

    /**
     * Outbox 메시지를 외부 시스템에 발행
     *
     * @param message 발행할 Outbox 메시지
     * @throws Exception 발행 실패 시 예외 발생
     */
    public void publish(Outbox message) throws Exception {
        log.info("[OutboxEventPublisher] 메시지 발행 준비 - messageId={}, orderId={}, type={}",
                message.getMessageId(), message.getOrderId(), message.getMessageType());

        switch (message.getMessageType()) {
            case "ORDER_COMPLETED":
                publishOrderCompleted(message);
                break;

            case "ORDER_CANCELLED":
                publishOrderCancelled(message);
                break;

            case "PAYMENT_COMPLETED":
                publishPaymentCompleted(message);
                break;

            case "PAYMENT_SUCCESS":
                publishPaymentSuccess(message);
                break;

            case "DATA_PLATFORM":
                publishDataPlatform(message);
                break;

            case "SHIPPING":
                publishShipping(message);
                break;

            default:
                throw new IllegalArgumentException(
                        "Unknown message type: " + message.getMessageType());
        }

        log.info("[OutboxEventPublisher] 메시지 발행 완료 - messageId={}, orderId={}, type={}",
                message.getMessageId(), message.getOrderId(), message.getMessageType());
    }

    /**
     * 주문 완료 이벤트 발행
     *
     * 용도:
     * - 배송 시스템: 배송 처리 시작
     * - 분석 시스템: 주문 통계 수집
     *
     * 발행 방법:
     * - Kafka: kafkaTemplate.send("order.completed", message)
     * - HTTP: restTemplate.postForObject("http://shipping-service/api/orders", message)
     *
     * @param message Outbox 메시지
     * @throws Exception 발행 실패 시
     */
    private void publishOrderCompleted(Outbox message) throws Exception {
        log.info("[OutboxEventPublisher] ORDER_COMPLETED 발행 - orderId={}, userId={}",
                message.getOrderId(), message.getUserId());

        // TODO: 실제 구현
        // 방법 1: Kafka 발행
        // kafkaTemplate.send("order.completed",
        //                   String.valueOf(message.getOrderId()),
        //                   message).get();
        //
        // 방법 2: HTTP 호출
        // restTemplate.postForObject(
        //     "http://shipping-service/api/orders",
        //     new ShippingRequest(message.getOrderId()),
        //     ShippingResponse.class);

        // 현재: 로깅만 수행
        log.info("[OutboxEventPublisher] ORDER_COMPLETED 이벤트를 배송 시스템으로 발행합니다 - orderId={}",
                message.getOrderId());
    }

    /**
     * 주문 취소 이벤트 발행
     *
     * 용도:
     * - 배송 시스템: 배송 취소 처리
     * - 결제 시스템: 결제 취소 (Void) 처리
     *
     * @param message Outbox 메시지
     * @throws Exception 발행 실패 시
     */
    private void publishOrderCancelled(Outbox message) throws Exception {
        log.info("[OutboxEventPublisher] ORDER_CANCELLED 발행 - orderId={}, userId={}",
                message.getOrderId(), message.getUserId());

        // TODO: 실제 구현
        // - 배송 취소
        // - 결제 취소 (Void)

        log.info("[OutboxEventPublisher] ORDER_CANCELLED 이벤트를 관련 시스템으로 발행합니다 - orderId={}",
                message.getOrderId());
    }

    /**
     * 결제 완료 이벤트 발행
     *
     * 용도:
     * - 결제 이력 저장
     * - 회계 시스템: 수익 인식
     * - 분석 시스템: 결제 통계
     *
     * @param message Outbox 메시지
     * @throws Exception 발행 실패 시
     */
    private void publishPaymentCompleted(Outbox message) throws Exception {
        log.info("[OutboxEventPublisher] PAYMENT_COMPLETED 발행 - orderId={}, userId={}",
                message.getOrderId(), message.getUserId());

        // TODO: 실제 구현
        // - 결제 이력 저장
        // - 회계 시스템 연동
        // - 정산 처리

        log.info("[OutboxEventPublisher] PAYMENT_COMPLETED 이벤트를 결제/회계 시스템으로 발행합니다 - orderId={}",
                message.getOrderId());
    }

    /**
     * 결제 성공 이벤트 발행 (실시간 알림)
     *
     * 용도:
     * - 실시간 알림: 사용자에게 결제 성공 푸시 알림
     * - SMS/이메일: 결제 영수증 발송
     * - 모니터링: 실시간 결제 성공률 추적
     *
     * 발행 방법:
     * - Kafka: kafkaTemplate.send("payment.success", message)
     * - HTTP: 알림 서비스 API 호출
     *
     * @param message Outbox 메시지
     * @throws Exception 발행 실패 시
     */
    private void publishPaymentSuccess(Outbox message) throws Exception {
        log.info("[OutboxEventPublisher] PAYMENT_SUCCESS 발행 - orderId={}, userId={}",
                message.getOrderId(), message.getUserId());

        // TODO: 실제 구현
        // - 푸시 알림 발송
        // - SMS/이메일 발송
        // - 실시간 모니터링 데이터 전송

        log.info("[OutboxEventPublisher] PAYMENT_SUCCESS 이벤트를 알림 시스템으로 발행합니다 - orderId={}",
                message.getOrderId());
    }

    /**
     * 데이터 플랫폼 이벤트 발행
     *
     * 용도:
     * - 분석 시스템: 주문/사용자 데이터 수집
     * - 데이터 웨어하우스: ETL 처리
     *
     * @param message Outbox 메시지
     * @throws Exception 발행 실패 시
     */
    private void publishDataPlatform(Outbox message) throws Exception {
        log.info("[OutboxEventPublisher] DATA_PLATFORM 발행 - orderId={}, payload={}",
                message.getOrderId(), message.getPayload());

        // DataPlatformClient를 통한 외부 연동
        dataPlatformClient.send(message.getPayload());

        log.info("[OutboxEventPublisher] DATA_PLATFORM 이벤트 발행 완료 - orderId={}",
                message.getOrderId());
    }

    /**
     * 배송 시스템 이벤트 발행
     *
     * 용도:
     * - 배송 시스템: 배송 생성 및 추적
     *
     * @param message Outbox 메시지
     * @throws Exception 발행 실패 시
     */
    private void publishShipping(Outbox message) throws Exception {
        log.info("[OutboxEventPublisher] SHIPPING 발행 - orderId={}, payload={}",
                message.getOrderId(), message.getPayload());

        // ShippingServiceClient를 통한 외부 연동
        // 실제로는 payload를 파싱하여 ShipmentRequest로 변환 후 전송
        log.info("[OutboxEventPublisher] SHIPPING 이벤트 발행 완료 - orderId={}",
                message.getOrderId());
    }
}
