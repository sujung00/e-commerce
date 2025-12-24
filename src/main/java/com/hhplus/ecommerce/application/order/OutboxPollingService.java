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
import java.util.Arrays;
import java.util.List;

/**
 * OutboxPollingService - Outbox 메시지 배치 처리 서비스 (중복 발행 방지 개선)
 *
 * 역할:
 * - 주기적으로 PENDING/FAILED 상태의 Outbox 메시지를 조회 (PUBLISHING 제외!)
 * - 각 메시지를 외부 시스템에 발행 시도
 * - 성공/실패에 따라 메시지 상태 업데이트
 * - 최대 재시도 횟수 초과 시 ABANDONED 상태로 전환
 * - PUBLISHING 타임아웃 메시지 처리 (멈춰있는 메시지 복구)
 *
 * 중복 발행 방지 메커니즘:
 * - PUBLISHING 상태의 메시지는 조회하지 않음
 * - 따라서 발행 성공 후 상태 업데이트 실패 시에도 재발행되지 않음
 * - PUBLISHING 상태가 일정 시간(5분) 이상 지속되면 타임아웃 처리
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
    private static final long PUBLISHING_TIMEOUT_MINUTES = 5;  // PUBLISHING 타임아웃 (5분)

    /**
     * 5초마다 실행되는 배치 작업 (중복 발행 방지 개선)
     *
     * 처리 흐름 (개선):
     * 1. PENDING과 FAILED 상태의 메시지만 조회 (PUBLISHING 제외!)
     * 2. PUBLISHING 타임아웃 메시지 처리 (5분 이상 멈춰있는 메시지)
     * 3. 각 메시지별로 외부 시스템 발행 시도
     * 4. 성공 → PUBLISHED로 업데이트
     * 5. 실패 → retryCount 증가, 최대 초과 시 ABANDONED로 변경
     *
     * 중복 발행 방지:
     * - PUBLISHING 상태의 메시지는 조회하지 않음
     * - 발행 성공 후 상태 업데이트 실패 시에도 PUBLISHING 상태 유지
     * - 따라서 배치가 재발행하지 않음 → 중복 발행 방지!
     */
    @Scheduled(fixedRate = 5000)
    @Transactional
    public void pollAndSendMessages() {
        try {
            log.debug("[OutboxPollingService] Outbox 메시지 폴링 시작...");

            // STEP 1: PENDING과 FAILED 상태의 메시지만 조회 (PUBLISHING 제외)
            List<Outbox> retryableMessages = outboxRepository.findByStatusIn(
                Arrays.asList("PENDING", "FAILED")
            );

            // STEP 2: PUBLISHING 타임아웃 메시지 처리 (멈춰있는 메시지 복구)
            LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(PUBLISHING_TIMEOUT_MINUTES);
            List<Outbox> stuckMessages = outboxRepository.findStuckPublishingMessages(timeoutThreshold);

            if (retryableMessages.isEmpty() && stuckMessages.isEmpty()) {
                log.debug("[OutboxPollingService] 전송할 메시지 없음 (PENDING/FAILED/STUCK)");
                return;
            }

            log.info("[OutboxPollingService] 발견된 메시지 - PENDING/FAILED: {}, STUCK PUBLISHING: {}",
                retryableMessages.size(), stuckMessages.size());

            // STEP 3: PUBLISHING 타임아웃 메시지를 FAILED로 변경 (재시도 대상으로 전환)
            for (Outbox stuck : stuckMessages) {
                log.warn("[OutboxPollingService] PUBLISHING 타임아웃 감지 - messageId={}, orderId={}, lastAttempt={}",
                    stuck.getMessageId(), stuck.getOrderId(), stuck.getLastAttempt());

                stuck.setStatus("FAILED");
                outboxRepository.update(stuck);

                log.info("[OutboxPollingService] PUBLISHING → FAILED 전환 완료 - messageId={}", stuck.getMessageId());
            }

            // STEP 4: 각 메시지별로 외부 시스템에 발행 시도
            for (Outbox message : retryableMessages) {
                processMessage(message);
            }

            log.debug("[OutboxPollingService] Outbox 메시지 폴링 완료");

        } catch (Exception e) {
            log.error("[OutboxPollingService] 배치 처리 중 예상치 못한 에러", e);
        }
    }

    /**
     * 개별 Outbox 메시지 처리 (중복 발행 방지 개선)
     *
     * 처리 흐름:
     * 1. PUBLISHING 상태로 변경 (발행 중 표시)
     * 2. 외부 시스템에 메시지 발행
     * 3. 성공 → PUBLISHED 상태로 업데이트
     * 4. 실패 → FAILED 상태로 업데이트
     *
     * @param message 처리할 Outbox 메시지
     */
    private void processMessage(Outbox message) {
        try {
            log.info("[OutboxPollingService] 메시지 발행 시작 - messageId={}, orderId={}, type={}",
                    message.getMessageId(), message.getOrderId(), message.getMessageType());

            // STEP 1: PUBLISHING 상태로 변경 (중복 발행 방지)
            message.markAsPublishing();
            outboxRepository.update(message);

            log.info("[OutboxPollingService] 발행 시작 - messageId={}, status=PUBLISHING",
                    message.getMessageId());

            // STEP 2: 외부 시스템에 메시지 발행
            eventPublisher.publish(message);

            // STEP 3: 성공 - PUBLISHED 상태로 업데이트
            message.markAsPublished();
            outboxRepository.update(message);

            log.info("[OutboxPollingService] 메시지 발행 성공 - messageId={}, orderId={}, status=PUBLISHED",
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
