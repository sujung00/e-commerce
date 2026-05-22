package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.domain.order.Outbox;
import com.hhplus.ecommerce.domain.order.OutboxRepository;
import com.hhplus.ecommerce.application.alert.AlertService;
import com.hhplus.ecommerce.infrastructure.constants.RetryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OutboxPollingService - Outbox 메시지 배치 처리 서비스
 *
 * 역할:
 * - 주기적으로 PENDING 상태의 Outbox 메시지를 조회
 * - 각 메시지를 외부 시스템에 발행 시도
 * - 성공/실패에 따라 메시지 상태 업데이트
 * - 최대 재시도 횟수 초과 시 ABANDONED 상태로 전환
 *
 * 실행:
 * - @Scheduled(fixedRate = 5000): 5초마다 실행
 * - 트랜잭션: 각 메시지별로 개별 트랜잭션 처리
 *
 * 설계:
 * - OutboxEventPublisher에 실제 발행 로직 위임
 * - 재시도 횟수 제한 (MAX_RETRIES)
 * - 타임스탬프 추적 (lastAttempt, sentAt)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxPollingService {

    private final OutboxRepository outboxRepository;
    private final OutboxEventPublisher eventPublisher;
    private final AlertService alertService;
    private final RetryProperties retryProperties;

    /**
     * 5초마다 실행되는 배치 작업
     *
     * 처리 흐름:
     * 1. PENDING 상태의 모든 Outbox 메시지 조회
     * 2. 각 메시지별로 외부 시스템 발행 시도
     * 3. 성공 → SENT로 업데이트
     * 4. 실패 → retryCount 증가, 최대 초과 시 ABANDONED로 변경
     */
    @Scheduled(fixedRateString = "${retry.outbox.polling-interval-ms:5000}")
    public void pollAndSendMessages() {
        log.debug("[OutboxPollingService] Outbox 메시지 폴링 시작...");

        List<Outbox> pendingMessages;
        try {
            pendingMessages = outboxRepository.findAllByStatus("PENDING");
        } catch (Exception e) {
            log.error("[OutboxPollingService] PENDING 메시지 조회 실패 (다음 폴링에서 재시도): error={}", e.getMessage(), e);
            return;
        }

        if (pendingMessages.isEmpty()) {
            log.debug("[OutboxPollingService] 전송할 PENDING 메시지 없음");
            return;
        }

        log.info("[OutboxPollingService] {} 개의 PENDING 메시지 발견", pendingMessages.size());

        // 메시지별 독립 처리 — 한 건 실패가 다른 건에 영향을 주지 않는다
        for (Outbox message : pendingMessages) {
            processMessage(message);
        }

        log.debug("[OutboxPollingService] Outbox 메시지 폴링 완료");
    }

    /**
     * 개별 Outbox 메시지 처리
     *
     * @param message 처리할 Outbox 메시지
     */
    @Transactional
    public void processMessage(Outbox message) {
        try {
            log.info("[OutboxPollingService] 메시지 발행 시작 - messageId={}, orderId={}, type={}",
                    message.getMessageId(), message.getOrderId(), message.getMessageType());

            eventPublisher.publish(message);

            message.markAsSent();
            message.setSentAt(LocalDateTime.now());
            outboxRepository.update(message);

            log.info("[OutboxPollingService] 메시지 발행 성공 - messageId={}, orderId={}, status=SENT",
                    message.getMessageId(), message.getOrderId());

        } catch (Exception e) {
            handleMessageFailure(message, e);
        }
    }

    /**
     * 메시지 발행 실패 처리
     *
     * 재시도 로직:
     * - retryCount < MAX_RETRIES: PENDING 상태 유지, 재시도 대기
     * - retryCount >= MAX_RETRIES: ABANDONED 상태로 변경, 관리자 알림
     *
     * @param message 실패한 Outbox 메시지
     * @param e 발생한 예외
     */
    private void handleMessageFailure(Outbox message, Exception e) {
        log.warn("[OutboxPollingService] 메시지 발행 실패 - messageId={}, orderId={}, error={}",
                message.getMessageId(), message.getOrderId(), e.getMessage());

        message.markAsFailed();          // status = "FAILED", retryCount++
        message.setLastAttempt(LocalDateTime.now());

        int maxAttempts = retryProperties.getOutbox().getMaxAttempts();
        try {
            if (message.getRetryCount() >= maxAttempts) {
                log.error("[OutboxPollingService] 최대 재시도 횟수 초과 → ABANDONED: messageId={}, orderId={}, retries={}",
                        message.getMessageId(), message.getOrderId(), message.getRetryCount());

                message.setStatus("ABANDONED");
                outboxRepository.update(message);
                alertService.notifyOutboxFailure(message);

            } else {
                outboxRepository.update(message);
                log.info("[OutboxPollingService] 메시지 FAILED 저장 (재시도 예정) - messageId={}, orderId={}, retryCount={}/{}",
                        message.getMessageId(), message.getOrderId(), message.getRetryCount(), maxAttempts);
            }
        } catch (Exception updateError) {
            // 상태 업데이트마저 실패하면 다음 폴링에서 중복 처리될 수 있으므로 반드시 로깅
            log.error("[OutboxPollingService] 메시지 상태 업데이트 실패 (다음 폴링에서 재처리됨): messageId={}, orderId={}, updateError={}",
                    message.getMessageId(), message.getOrderId(), updateError.getMessage(), updateError);
        }
    }
}
