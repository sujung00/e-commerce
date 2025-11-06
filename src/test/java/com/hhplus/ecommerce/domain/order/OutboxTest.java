package com.hhplus.ecommerce.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outbox 도메인 엔티티 단위 테스트
 * - Outbox 메시지 생성
 * - 상태 전이 (PENDING → SENT/FAILED)
 * - 재시도 관리
 */
@DisplayName("Outbox 도메인 엔티티 테스트")
class OutboxTest {

    private static final Long TEST_ORDER_ID = 1L;
    private static final Long TEST_USER_ID = 100L;
    private static final String TEST_MESSAGE_TYPE = "ORDER_COMPLETED";

    // ========== Outbox 생성 팩토리 메서드 ==========

    @Test
    @DisplayName("Outbox 생성 - 성공")
    void testCreateOutbox_Success() {
        // When
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, TEST_MESSAGE_TYPE);

        // Then
        assertNotNull(outbox);
        assertEquals(TEST_ORDER_ID, outbox.getOrderId());
        assertEquals(TEST_USER_ID, outbox.getUserId());
        assertEquals(TEST_MESSAGE_TYPE, outbox.getMessageType());
        assertEquals("PENDING", outbox.getStatus());
        assertEquals(0, outbox.getRetryCount());
        assertNull(outbox.getLastAttempt());
        assertNull(outbox.getSentAt());
        assertNotNull(outbox.getCreatedAt());
    }

    @Test
    @DisplayName("Outbox 생성 - 초기 상태는 PENDING")
    void testCreateOutbox_InitialStatusPending() {
        // When
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, "SHIPPING_REQUEST");

        // Then
        assertEquals("PENDING", outbox.getStatus());
    }

    @Test
    @DisplayName("Outbox 생성 - 재시도 횟수는 0으로 초기화")
    void testCreateOutbox_RetryCountZero() {
        // When
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, TEST_MESSAGE_TYPE);

        // Then
        assertEquals(0, outbox.getRetryCount());
    }

    @Test
    @DisplayName("Outbox 생성 - 다양한 메시지 타입")
    void testCreateOutbox_DifferentMessageTypes() {
        // When
        Outbox outbox1 = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, "ORDER_COMPLETED");
        Outbox outbox2 = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, "SHIPPING_REQUEST");
        Outbox outbox3 = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, "PAYMENT_CONFIRMED");

        // Then
        assertEquals("ORDER_COMPLETED", outbox1.getMessageType());
        assertEquals("SHIPPING_REQUEST", outbox2.getMessageType());
        assertEquals("PAYMENT_CONFIRMED", outbox3.getMessageType());
    }

    // ========== 상태 전이: PENDING → SENT ==========

    @Test
    @DisplayName("markAsSent - 상태를 SENT로 변경")
    void testMarkAsSent_StatusChange() {
        // Given
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, TEST_MESSAGE_TYPE);
        assertEquals("PENDING", outbox.getStatus());

        // When
        outbox.markAsSent();

        // Then
        assertEquals("SENT", outbox.getStatus());
    }

    @Test
    @DisplayName("markAsSent - 전송 완료 시간 기록")
    void testMarkAsSent_SentAtSet() {
        // Given
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, TEST_MESSAGE_TYPE);
        assertNull(outbox.getSentAt());

        // When
        LocalDateTime beforeSent = LocalDateTime.now();
        outbox.markAsSent();
        LocalDateTime afterSent = LocalDateTime.now();

        // Then
        assertNotNull(outbox.getSentAt());
        assertFalse(outbox.getSentAt().isBefore(beforeSent));
        assertFalse(outbox.getSentAt().isAfter(afterSent.plusSeconds(1)));
    }

    @Test
    @DisplayName("markAsSent - 재시도 횟수는 유지")
    void testMarkAsSent_RetryCountUnchanged() {
        // Given
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, TEST_MESSAGE_TYPE);
        outbox.setRetryCount(3);

        // When
        outbox.markAsSent();

        // Then
        assertEquals(3, outbox.getRetryCount());
    }

    // ========== 상태 전이: PENDING → FAILED ==========

    @Test
    @DisplayName("markAsFailed - 상태를 FAILED로 변경")
    void testMarkAsFailed_StatusChange() {
        // Given
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, TEST_MESSAGE_TYPE);
        assertEquals("PENDING", outbox.getStatus());

        // When
        outbox.markAsFailed();

        // Then
        assertEquals("FAILED", outbox.getStatus());
    }

    @Test
    @DisplayName("markAsFailed - 마지막 시도 시간 기록")
    void testMarkAsFailed_LastAttemptSet() {
        // Given
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, TEST_MESSAGE_TYPE);
        assertNull(outbox.getLastAttempt());

        // When
        LocalDateTime beforeFailed = LocalDateTime.now();
        outbox.markAsFailed();
        LocalDateTime afterFailed = LocalDateTime.now();

        // Then
        assertNotNull(outbox.getLastAttempt());
        assertFalse(outbox.getLastAttempt().isBefore(beforeFailed));
        assertFalse(outbox.getLastAttempt().isAfter(afterFailed.plusSeconds(1)));
    }

    @Test
    @DisplayName("markAsFailed - 재시도 횟수 증가")
    void testMarkAsFailed_RetryCountIncrement() {
        // Given
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, TEST_MESSAGE_TYPE);
        assertEquals(0, outbox.getRetryCount());

        // When
        outbox.markAsFailed();

        // Then
        assertEquals(1, outbox.getRetryCount());
    }

    @Test
    @DisplayName("markAsFailed - 여러 번 실패 시 재시도 횟수 누적")
    void testMarkAsFailed_MultipleRetries() {
        // Given
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, TEST_MESSAGE_TYPE);

        // When
        outbox.markAsFailed();
        outbox.markAsFailed();
        outbox.markAsFailed();

        // Then
        assertEquals(3, outbox.getRetryCount());
        assertEquals("FAILED", outbox.getStatus());
    }

    // ========== 상태 전이: FAILED → PENDING (재시도) ==========

    @Test
    @DisplayName("resetForRetry - 상태를 PENDING으로 변경")
    void testResetForRetry_StatusChange() {
        // Given
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, TEST_MESSAGE_TYPE);
        outbox.markAsFailed();
        assertEquals("FAILED", outbox.getStatus());

        // When
        outbox.resetForRetry();

        // Then
        assertEquals("PENDING", outbox.getStatus());
    }

    @Test
    @DisplayName("resetForRetry - 마지막 시도 시간 업데이트")
    void testResetForRetry_LastAttemptUpdated() throws InterruptedException {
        // Given
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, TEST_MESSAGE_TYPE);
        outbox.markAsFailed();
        LocalDateTime firstAttempt = outbox.getLastAttempt();

        // When
        Thread.sleep(100);  // 시간 차이 생성
        outbox.resetForRetry();
        LocalDateTime secondAttempt = outbox.getLastAttempt();

        // Then
        assertNotNull(secondAttempt);
        assertTrue(secondAttempt.isAfter(firstAttempt));
    }

    @Test
    @DisplayName("resetForRetry - 재시도 횟수는 유지")
    void testResetForRetry_RetryCountUnchanged() {
        // Given
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, TEST_MESSAGE_TYPE);
        outbox.markAsFailed();
        outbox.markAsFailed();
        int retryCountBefore = outbox.getRetryCount();

        // When
        outbox.resetForRetry();

        // Then
        assertEquals(retryCountBefore, outbox.getRetryCount());
    }

    // ========== 상태 전이 시나리오 ==========

    @Test
    @DisplayName("상태 전이 시나리오 - PENDING → FAILED → PENDING → SENT")
    void testStateTransitionScenario() {
        // Given
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, TEST_MESSAGE_TYPE);

        // When/Then
        assertEquals("PENDING", outbox.getStatus());
        assertEquals(0, outbox.getRetryCount());

        // 첫 번째 시도 실패
        outbox.markAsFailed();
        assertEquals("FAILED", outbox.getStatus());
        assertEquals(1, outbox.getRetryCount());

        // 재시도 준비
        outbox.resetForRetry();
        assertEquals("PENDING", outbox.getStatus());
        assertEquals(1, outbox.getRetryCount());

        // 두 번째 시도 성공
        outbox.markAsSent();
        assertEquals("SENT", outbox.getStatus());
        assertNotNull(outbox.getSentAt());
    }

    @Test
    @DisplayName("상태 전이 시나리오 - 여러 번 실패 후 성공")
    void testStateTransitionScenario_MultipleFailuresBeforeSuccess() {
        // Given
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, TEST_MESSAGE_TYPE);

        // When/Then
        // 1차 시도 실패
        outbox.markAsFailed();
        assertEquals(1, outbox.getRetryCount());

        // 2차 시도 준비 및 실패
        outbox.resetForRetry();
        outbox.markAsFailed();
        assertEquals(2, outbox.getRetryCount());

        // 3차 시도 준비 및 실패
        outbox.resetForRetry();
        outbox.markAsFailed();
        assertEquals(3, outbox.getRetryCount());

        // 4차 시도 준비 및 성공
        outbox.resetForRetry();
        outbox.markAsSent();
        assertEquals("SENT", outbox.getStatus());
        assertEquals(3, outbox.getRetryCount());  // 최종 재시도 횟수
    }

    // ========== Outbox 빌더 패턴 ==========

    @Test
    @DisplayName("Outbox 빌더 - 모든 필드 설정")
    void testOutboxBuilder_AllFields() {
        // When
        LocalDateTime now = LocalDateTime.now();
        Outbox outbox = Outbox.builder()
                .messageId(1L)
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .messageType(TEST_MESSAGE_TYPE)
                .status("SENT")
                .retryCount(2)
                .lastAttempt(now)
                .sentAt(now)
                .createdAt(now)
                .build();

        // Then
        assertEquals(1L, outbox.getMessageId());
        assertEquals(TEST_ORDER_ID, outbox.getOrderId());
        assertEquals("SENT", outbox.getStatus());
        assertEquals(2, outbox.getRetryCount());
    }

    // ========== 경계값 테스트 ==========

    @Test
    @DisplayName("경계값 - 재시도 횟수 0")
    void testBoundary_RetryCountZero() {
        // When
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, TEST_MESSAGE_TYPE);

        // Then
        assertEquals(0, outbox.getRetryCount());
    }

    @Test
    @DisplayName("경계값 - 재시도 횟수 증가")
    void testBoundary_RetryCountIncreasing() {
        // Given
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, TEST_MESSAGE_TYPE);

        // When/Then
        for (int i = 0; i < 100; i++) {
            outbox.markAsFailed();
            assertEquals(i + 1, outbox.getRetryCount());
        }
    }

    @Test
    @DisplayName("경계값 - ID 값 범위")
    void testBoundary_IdValues() {
        // When
        Outbox outbox = Outbox.createOutbox(Long.MAX_VALUE, Long.MAX_VALUE, TEST_MESSAGE_TYPE);

        // Then
        assertEquals(Long.MAX_VALUE, outbox.getOrderId());
        assertEquals(Long.MAX_VALUE, outbox.getUserId());
    }

    @Test
    @DisplayName("필드 null 안전성")
    void testNullSafety() {
        // When
        Outbox outbox = Outbox.builder().build();

        // Then
        assertNull(outbox.getMessageId());
        assertNull(outbox.getOrderId());
        assertNull(outbox.getStatus());
        assertNull(outbox.getRetryCount());
    }

    @Test
    @DisplayName("Setter를 통한 상태 변경")
    void testSetterForStatusChange() {
        // Given
        Outbox outbox = Outbox.createOutbox(TEST_ORDER_ID, TEST_USER_ID, TEST_MESSAGE_TYPE);

        // When
        outbox.setStatus("CUSTOM_STATUS");
        outbox.setRetryCount(10);

        // Then
        assertEquals("CUSTOM_STATUS", outbox.getStatus());
        assertEquals(10, outbox.getRetryCount());
    }
}
