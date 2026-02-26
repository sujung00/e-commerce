package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.order.OutboxEventPublisher;
import com.hhplus.ecommerce.application.order.OutboxPollingService;
import com.hhplus.ecommerce.domain.order.Outbox;
import com.hhplus.ecommerce.domain.order.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox 패턴 중복 발행 방지 통합 테스트
 *
 * 테스트 시나리오:
 * 1. 정상 흐름: PENDING → PUBLISHING → PUBLISHED
 * 2. 상태 업데이트 실패 시나리오: publish() 성공 후 상태 업데이트 실패 → PUBLISHING 유지 → 재발행 안 됨
 * 3. PUBLISHING 타임아웃: 멈춰있는 메시지 복구
 * 4. 배치 처리 중복 방지: PUBLISHING 상태는 조회되지 않음
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Outbox 중복 발행 방지 통합 테스트")
class OutboxDuplicatePreventionTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxEventPublisher outboxEventPublisher;

    @Autowired
    private OutboxPollingService outboxPollingService;

    private static final Long TEST_ORDER_ID = 1000L;
    private static final Long TEST_USER_ID = 100L;
    private static final String TEST_PAYLOAD = "{\"orderId\": 1000, \"amount\": 10000}";

    @BeforeEach
    void setUp() {
        // 테스트 전 Outbox 테이블 초기화
        outboxRepository.findAll().forEach(outbox -> {
            outboxRepository.update(outbox);
        });
    }

    // ========== 1. 정상 흐름 테스트 ==========

    @Test
    @DisplayName("정상 흐름: PENDING → PUBLISHING → PUBLISHED")
    void testNormalFlow_StatusTransition() throws Exception {
        // Given: PENDING 상태의 Outbox 메시지 생성
        Outbox outbox = Outbox.createOutboxWithPayload(
                TEST_ORDER_ID,
                TEST_USER_ID,
                "ORDER_COMPLETED",
                TEST_PAYLOAD
        );
        Outbox saved = outboxRepository.save(outbox);

        // Then: 초기 상태는 PENDING
        assertThat(saved.getStatus()).isEqualTo("PENDING");

        // When: publishWithOutbox 호출 (즉시 발행 시도)
        outboxEventPublisher.publishWithOutbox(
                "ORDER_COMPLETED",
                TEST_ORDER_ID + 1,
                TEST_USER_ID,
                TEST_PAYLOAD
        );

        // Then: 새로운 메시지는 PUBLISHED 상태
        List<Outbox> messages = outboxRepository.findByOrderId(TEST_ORDER_ID + 1);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getStatus()).isEqualTo("PUBLISHED");
        assertThat(messages.get(0).getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("배치 처리: PENDING → PUBLISHING → PUBLISHED")
    void testBatchProcessing_StatusTransition() {
        // Given: PENDING 상태의 Outbox 메시지 생성
        Outbox outbox = Outbox.createOutboxWithPayload(
                TEST_ORDER_ID,
                TEST_USER_ID,
                "ORDER_COMPLETED",
                TEST_PAYLOAD
        );
        outboxRepository.save(outbox);

        // When: 배치 처리 실행
        outboxPollingService.pollAndSendMessages();

        // Then: 상태가 PUBLISHED로 변경됨
        List<Outbox> messages = outboxRepository.findByOrderId(TEST_ORDER_ID);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getStatus()).isEqualTo("PUBLISHED");
        assertThat(messages.get(0).getSentAt()).isNotNull();
    }

    // ========== 2. 중복 발행 방지 테스트 ==========

    @Test
    @DisplayName("중복 발행 방지: PUBLISHING 상태는 배치 처리에서 제외")
    void testDuplicatePrevention_PublishingExcludedFromBatch() {
        // Given: PUBLISHING 상태의 메시지 생성 (발행 중 상태 업데이트 실패 시나리오)
        Outbox outbox = Outbox.createOutboxWithPayload(
                TEST_ORDER_ID,
                TEST_USER_ID,
                "ORDER_COMPLETED",
                TEST_PAYLOAD
        );
        outbox.markAsPublishing();  // PUBLISHING 상태로 변경
        outboxRepository.save(outbox);

        // When: 배치 처리 실행
        outboxPollingService.pollAndSendMessages();

        // Then: PUBLISHING 상태는 조회되지 않아 재발행되지 않음
        List<Outbox> messages = outboxRepository.findByOrderId(TEST_ORDER_ID);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getStatus()).isEqualTo("PUBLISHING");  // 상태 유지
        assertThat(messages.get(0).getSentAt()).isNull();  // 발행 완료 시간 없음
    }

    @Test
    @DisplayName("중복 발행 방지: PENDING과 FAILED만 배치 처리 대상")
    void testDuplicatePrevention_OnlyPendingAndFailedProcessed() {
        // Given: 여러 상태의 메시지 생성
        Outbox pending = Outbox.createOutboxWithPayload(TEST_ORDER_ID, TEST_USER_ID, "ORDER_COMPLETED", TEST_PAYLOAD);
        pending.setStatus("PENDING");
        outboxRepository.save(pending);

        Outbox failed = Outbox.createOutboxWithPayload(TEST_ORDER_ID + 1, TEST_USER_ID, "ORDER_COMPLETED", TEST_PAYLOAD);
        failed.setStatus("FAILED");
        outboxRepository.save(failed);

        Outbox publishing = Outbox.createOutboxWithPayload(TEST_ORDER_ID + 2, TEST_USER_ID, "ORDER_COMPLETED", TEST_PAYLOAD);
        publishing.setStatus("PUBLISHING");
        outboxRepository.save(publishing);

        Outbox published = Outbox.createOutboxWithPayload(TEST_ORDER_ID + 3, TEST_USER_ID, "ORDER_COMPLETED", TEST_PAYLOAD);
        published.setStatus("PUBLISHED");
        outboxRepository.save(published);

        // When: 배치 처리 조회 쿼리 실행
        List<Outbox> retryableMessages = outboxRepository.findByStatusIn(
                Arrays.asList("PENDING", "FAILED")
        );

        // Then: PENDING과 FAILED만 조회됨
        assertThat(retryableMessages).hasSize(2);
        assertThat(retryableMessages)
                .extracting(Outbox::getStatus)
                .containsExactlyInAnyOrder("PENDING", "FAILED");
    }

    // ========== 3. PUBLISHING 타임아웃 테스트 ==========

    @Test
    @DisplayName("PUBLISHING 타임아웃: 5분 이상 멈춰있는 메시지 복구")
    void testPublishingTimeout_StuckMessageRecovery() {
        // Given: PUBLISHING 상태가 5분 이상 지난 메시지 생성
        Outbox stuck = Outbox.createOutboxWithPayload(
                TEST_ORDER_ID,
                TEST_USER_ID,
                "ORDER_COMPLETED",
                TEST_PAYLOAD
        );
        stuck.setStatus("PUBLISHING");
        stuck.setLastAttempt(LocalDateTime.now().minusMinutes(10));  // 10분 전
        outboxRepository.save(stuck);

        // When: 타임아웃 임계값으로 조회
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        List<Outbox> stuckMessages = outboxRepository.findStuckPublishingMessages(threshold);

        // Then: 타임아웃된 메시지 발견
        assertThat(stuckMessages).hasSize(1);
        assertThat(stuckMessages.get(0).getMessageId()).isEqualTo(stuck.getMessageId());
        assertThat(stuckMessages.get(0).getStatus()).isEqualTo("PUBLISHING");
    }

    @Test
    @DisplayName("PUBLISHING 타임아웃: 배치 처리에서 FAILED로 전환")
    void testPublishingTimeout_ConvertedToFailed() {
        // Given: PUBLISHING 상태가 5분 이상 지난 메시지 생성
        Outbox stuck = Outbox.createOutboxWithPayload(
                TEST_ORDER_ID,
                TEST_USER_ID,
                "ORDER_COMPLETED",
                TEST_PAYLOAD
        );
        stuck.setStatus("PUBLISHING");
        stuck.setLastAttempt(LocalDateTime.now().minusMinutes(10));  // 10분 전
        outboxRepository.save(stuck);

        // When: 배치 처리 실행 (타임아웃 메시지 처리)
        outboxPollingService.pollAndSendMessages();

        // Then: FAILED 상태로 전환됨
        List<Outbox> messages = outboxRepository.findByOrderId(TEST_ORDER_ID);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("PUBLISHING 타임아웃: 정상 PUBLISHING 메시지는 영향 없음")
    void testPublishingTimeout_NormalPublishingNotAffected() {
        // Given: 정상 PUBLISHING 메시지 (1분 전)
        Outbox normalPublishing = Outbox.createOutboxWithPayload(
                TEST_ORDER_ID,
                TEST_USER_ID,
                "ORDER_COMPLETED",
                TEST_PAYLOAD
        );
        normalPublishing.setStatus("PUBLISHING");
        normalPublishing.setLastAttempt(LocalDateTime.now().minusMinutes(1));  // 1분 전
        outboxRepository.save(normalPublishing);

        // When: 타임아웃 조회 (5분 기준)
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        List<Outbox> stuckMessages = outboxRepository.findStuckPublishingMessages(threshold);

        // Then: 조회되지 않음
        assertThat(stuckMessages).isEmpty();
    }

    // ========== 4. 상태 전이 검증 테스트 ==========

    @Test
    @DisplayName("상태 전이 검증: markAsPublishing() 호출 시 PUBLISHING 상태")
    void testStateTransition_MarkAsPublishing() {
        // Given
        Outbox outbox = Outbox.createOutboxWithPayload(
                TEST_ORDER_ID,
                TEST_USER_ID,
                "ORDER_COMPLETED",
                TEST_PAYLOAD
        );
        assertThat(outbox.getStatus()).isEqualTo("PENDING");

        // When
        outbox.markAsPublishing();

        // Then
        assertThat(outbox.getStatus()).isEqualTo("PUBLISHING");
        assertThat(outbox.getLastAttempt()).isNotNull();
    }

    @Test
    @DisplayName("상태 전이 검증: markAsPublished() 호출 시 PUBLISHED 상태")
    void testStateTransition_MarkAsPublished() {
        // Given
        Outbox outbox = Outbox.createOutboxWithPayload(
                TEST_ORDER_ID,
                TEST_USER_ID,
                "ORDER_COMPLETED",
                TEST_PAYLOAD
        );
        outbox.markAsPublishing();

        // When
        outbox.markAsPublished();

        // Then
        assertThat(outbox.getStatus()).isEqualTo("PUBLISHED");
        assertThat(outbox.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("상태 전이 검증: PENDING → PUBLISHING → PUBLISHED 전체 플로우")
    void testStateTransition_FullFlow() {
        // Given: PENDING
        Outbox outbox = Outbox.createOutboxWithPayload(
                TEST_ORDER_ID,
                TEST_USER_ID,
                "ORDER_COMPLETED",
                TEST_PAYLOAD
        );
        assertThat(outbox.getStatus()).isEqualTo("PENDING");

        // When: PENDING → PUBLISHING
        outbox.markAsPublishing();
        assertThat(outbox.getStatus()).isEqualTo("PUBLISHING");
        assertThat(outbox.getLastAttempt()).isNotNull();

        // When: PUBLISHING → PUBLISHED
        outbox.markAsPublished();
        assertThat(outbox.getStatus()).isEqualTo("PUBLISHED");
        assertThat(outbox.getSentAt()).isNotNull();
    }

    // ========== 5. 멱등성 검증 테스트 ==========

    @Test
    @DisplayName("멱등성 검증: 동일 메시지 중복 발행 시도 시 한 번만 발행")
    void testIdempotency_MultiplePublishAttempts() {
        // Given: PENDING 상태의 메시지
        Outbox outbox = Outbox.createOutboxWithPayload(
                TEST_ORDER_ID,
                TEST_USER_ID,
                "ORDER_COMPLETED",
                TEST_PAYLOAD
        );
        Outbox saved = outboxRepository.save(outbox);

        // When: 배치 처리 여러 번 실행
        outboxPollingService.pollAndSendMessages();

        // 첫 번째 실행 후 상태 확인
        Outbox after1st = outboxRepository.findById(saved.getMessageId()).get();
        assertThat(after1st.getStatus()).isEqualTo("PUBLISHED");

        // 두 번째 실행
        outboxPollingService.pollAndSendMessages();

        // Then: 여전히 PUBLISHED 상태 유지 (재발행되지 않음)
        Outbox after2nd = outboxRepository.findById(saved.getMessageId()).get();
        assertThat(after2nd.getStatus()).isEqualTo("PUBLISHED");
        assertThat(after2nd.getSentAt()).isEqualTo(after1st.getSentAt());  // 발행 시간 동일
    }
}
