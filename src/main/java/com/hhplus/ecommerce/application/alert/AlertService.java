package com.hhplus.ecommerce.application.alert;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AlertService - 관리자 알림 서비스 (Application 계층)
 *
 * 역할:
 * - 시스템 이벤트 (결제 실패, 보상 처리 등)에 대한 관리자 알림 발송
 * - 실제 구현은 이메일, SMS, Slack 등 다양한 채널 가능
 *
 * 현재 구현:
 * - 로깅 기반 알림 (프로덕션에서는 이메일/메시지 큐로 확장 가능)
 * - 향후 AlertRepository를 통해 알림 이력 저장 가능
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    /**
     * 결제 실패 알림
     *
     * 시나리오:
     * - 외부 결제 API 호출 실패 (타임아웃, 네트워크, 5xx)
     * - 보상 트랜잭션 시작 필수
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param amount 결제 금액
     * @param reason 실패 원인
     */
    public void notifyPaymentFailure(Long orderId, Long userId, Long amount, String reason) {
        String message = String.format(
                "[결제 실패 알림] 주문 ID: %d, 사용자 ID: %d, 금액: %d원, 원인: %s",
                orderId, userId, amount, reason
        );

        log.error(message);
        // TODO: 프로덕션에서는 실제 알림 채널로 발송
        // - 이메일: admin@company.com
        // - Slack: #payments-alert
        // - SMS: 관리자 연락처
    }

    /**
     * 보상 트랜잭션 완료 알림
     *
     * 시나리오:
     * - 결제 실패 후 재고/잔액 복구 성공
     * - 주문 상태를 CANCELLED로 변경
     * - 관리자가 고객 지원 필요 여부 판단
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param refundedAmount 환불액
     */
    public void notifyCompensationComplete(Long orderId, Long userId, Long refundedAmount) {
        String message = String.format(
                "[보상 처리 완료] 주문 ID: %d, 사용자 ID: %d, 환불액: %d원 - 고객 지원 필요 여부 확인 바랍니다",
                orderId, userId, refundedAmount
        );

        log.warn(message);
        // TODO: 프로덕션에서는 실제 알림 채널로 발송
    }

    /**
     * 보상 트랜잭션 실패 알림 (심각한 상황)
     *
     * 시나리오:
     * - 결제 실패 후 재고/잔액 복구 중 데이터베이스 오류
     * - 수동 개입 필요 (데이터 정합성 복구)
     * - 고객에게 환불이 정상 처리되도록 보장해야 함
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param error 에러 메시지
     */
    public void notifyCompensationFailure(Long orderId, Long userId, String error) {
        String message = String.format(
                "[보상 처리 실패 - 긴급!] 주문 ID: %d, 사용자 ID: %d, 에러: %s - 즉시 수동 개입 필요",
                orderId, userId, error
        );

        log.error(message);
        // TODO: 심각도를 높여 알림 발송
        // - 이메일: admin@company.com (높은 우선순위)
        // - Slack: @channel
        // - PagerDuty: Critical alert
    }

    /**
     * 결제 성공 알림 (선택사항)
     *
     * 시나리오:
     * - 결제 성공 후 주문 상태 PAID로 변경
     * - 관리자가 배송 등 후속 처리 확인
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param amount 결제 금액
     */
    public void notifyPaymentSuccess(Long orderId, Long userId, Long amount) {
        String message = String.format(
                "[결제 성공] 주문 ID: %d, 사용자 ID: %d, 금액: %d원",
                orderId, userId, amount
        );

        log.info(message);
        // TODO: 프로덕션에서는 실제 알림 채널로 발송 (선택사항)
    }

    /**
     * 결제 부분 성공 알림 (Void 필요)
     *
     * 시나리오:
     * - 카드 승인은 되었으나 3D Secure 인증 실패
     * - 승인된 부분에 대한 Void 처리 필요
     * - 수동 개입 가능성 높음
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param approvedAmount 승인된 금액
     * @param failedReason 실패 원인
     */
    public void notifyPartialPaymentSuccess(Long orderId, Long userId, Long approvedAmount, String failedReason) {
        String message = String.format(
                "[결제 부분 성공 - Void 필요] 주문 ID: %d, 사용자 ID: %d, 승인액: %d원, 실패: %s - 즉시 처리 필요",
                orderId, userId, approvedAmount, failedReason
        );

        log.warn(message);
        // TODO: 프로덕션에서는 높은 우선순위 알림 발송
    }

    /**
     * Outbox 메시지 발행 실패 알림 (최대 재시도 초과)
     *
     * 시나리오:
     * - 배치 프로세스에서 PENDING 메시지 발행 실패
     * - 최대 재시도 횟수(3회) 초과
     * - 메시지를 ABANDONED 상태로 전환
     * - 수동 개입으로 원인 파악 및 재처리 필요
     *
     * @param outbox 발행 실패한 Outbox 메시지
     */
    public void notifyOutboxFailure(com.hhplus.ecommerce.domain.order.Outbox outbox) {
        String message = String.format(
                "[Outbox 메시지 발행 실패 - DLQ로 이동] 메시지 ID: %d, 주문 ID: %d, 타입: %s, 재시도: %d회 - 즉시 확인 필요",
                outbox.getMessageId(), outbox.getOrderId(), outbox.getMessageType(), outbox.getRetryCount()
        );

        log.error(message);
        // TODO: 프로덕션에서는 높은 우선순위 알림 발송
        // - 이메일: ops@company.com
        // - Slack: #outbox-failures
        // - PagerDuty: Warning alert
    }

    /**
     * 재고 부족 알림
     *
     * 시나리오:
     * - 주문 처리 중 상품 재고가 임계값 이하로 떨어짐
     * - 재입고 요청 또는 관리자에게 알림 필요
     * - 품절 방지를 위한 선제적 조치
     *
     * @param productId 상품 ID
     * @param optionId 옵션 ID
     * @param productName 상품명
     * @param optionName 옵션명
     * @param currentStock 현재 재고
     * @param threshold 임계값
     */
    public void notifyLowInventory(Long productId, Long optionId, String productName, String optionName, Integer currentStock, Integer threshold) {
        String message = String.format(
                "[재고 부족 알림] 상품: %s, 옵션: %s (ID: %d/%d), 현재 재고: %d개, 임계값: %d개 - 재입고 필요",
                productName, optionName, productId, optionId, currentStock, threshold
        );

        log.warn(message);
        // TODO: 프로덕션에서는 실제 알림 채널로 발송
        // - 이메일: inventory@company.com
        // - Slack: #inventory-alerts
        // - 재입고 시스템: 자동 발주 트리거
    }
}
