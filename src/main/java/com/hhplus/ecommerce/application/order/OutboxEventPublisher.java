package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.domain.order.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * OutboxEventPublisher - Outbox 메시지 발행 서비스
 *
 * 역할:
 * - Outbox 메시지를 외부 시스템으로 발행
 * - 메시지 타입별로 적절한 발행 채널 선택
 *
 * 지원하는 메시지 타입:
 * - ORDER_COMPLETED: 주문 완료 이벤트 (배송, 알림 등)
 * - ORDER_CANCELLED: 주문 취소 이벤트 (재고 복구 등)
 * - PAYMENT_COMPLETED: 결제 완료 이벤트 (결제 이력 저장 등)
 *
 * 확장 가능성:
 * - Kafka: 메시지 큐를 통한 이벤트 발행
 * - HTTP: REST API를 통한 직접 호출
 * - 이메일/SMS: 고객 알림
 * - 데이터베이스: 이벤트 스토어 저장
 *
 * 현재 구현:
 * - 로깅 기반 (프로토타입)
 * - 향후 Kafka, HTTP 등으로 확장 가능
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxEventPublisher {

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
}
