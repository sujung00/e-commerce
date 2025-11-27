package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.domain.order.Outbox;
import com.hhplus.ecommerce.domain.order.OutboxRepository;
import com.hhplus.ecommerce.application.alert.AlertService;
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

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_SECONDS = 60;  // 1분 후 재시도

    /**
     * 5초마다 실행되는 배치 작업
     *
     * 처리 흐름:
     * 1. PENDING 상태의 모든 Outbox 메시지 조회
     * 2. 각 메시지별로 외부 시스템 발행 시도
     * 3. 성공 → SENT로 업데이트
     * 4. 실패 → retryCount 증가, 최대 초과 시 ABANDONED로 변경
     */
    @Scheduled(fixedRate = 5000)
    @Transactional
    public void pollAndSendMessages() {
        try {
            log.debug("[OutboxPollingService] Outbox 메시지 폴링 시작...");

            // STEP 1: PENDING 상태의 모든 메시지 조회
            List<Outbox> pendingMessages = outboxRepository.findAllByStatus("PENDING");

            if (pendingMessages.isEmpty()) {
                log.debug("[OutboxPollingService] 전송할 PENDING 메시지 없음");
                return;
            }

            log.info("[OutboxPollingService] {} 개의 PENDING 메시지 발견", pendingMessages.size());

            // STEP 2: 각 메시지별로 외부 시스템에 발행 시도
            for (Outbox message : pendingMessages) {
                processMessage(message);
            }

            log.debug("[OutboxPollingService] Outbox 메시지 폴링 완료");

        } catch (Exception e) {
            log.error("[OutboxPollingService] 배치 처리 중 예상치 못한 에러", e);
        }
    }

    /**
     * 개별 Outbox 메시지 처리
     *
     * @param message 처리할 Outbox 메시지
     */
    private void processMessage(Outbox message) {
        try {
            log.info("[OutboxPollingService] 메시지 발행 시작 - messageId={}, orderId={}, type={}",
                    message.getMessageId(), message.getOrderId(), message.getMessageType());

            // STEP 3a: 외부 시스템에 메시지 발행
            eventPublisher.publish(message);

            // STEP 3b: 성공 - SENT 상태로 업데이트
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

        // 재시도 횟수 증가
        message.markAsFailed();
        message.setLastAttempt(LocalDateTime.now());

        // 최대 재시도 횟수 확인
        if (message.getRetryCount() >= MAX_RETRIES) {
            log.error("[OutboxPollingService] 최대 재시도 횟수 초과 - messageId={}, orderId={}, retries={}",
                    message.getMessageId(), message.getOrderId(), message.getRetryCount());

            // DLQ로 이동 (ABANDONED)
            message.setStatus("ABANDONED");
            outboxRepository.update(message);

            // 관리자 알림
            alertService.notifyOutboxFailure(message);

            log.error("[OutboxPollingService] 메시지 ABANDONED로 처리됨 - messageId={}, orderId={}",
                    message.getMessageId(), message.getOrderId());

        } else {
            // 재시도 대기: PENDING 상태 유지, lastAttempt 업데이트
            message.setStatus("PENDING");
            outboxRepository.update(message);

            log.info("[OutboxPollingService] 메시지 재시도 대기 - messageId={}, orderId={}, retryCount={}/{}",
                    message.getMessageId(), message.getOrderId(), message.getRetryCount(), MAX_RETRIES);
        }
    }
}
