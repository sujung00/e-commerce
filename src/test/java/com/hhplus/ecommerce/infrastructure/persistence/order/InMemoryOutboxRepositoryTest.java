package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.Outbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InMemoryOutboxRepository 단위 테스트
 * - Outbox 메시지 저장 및 상태 관리
 * - 주문 ID별 메시지 조회
 * - 상태별 배치 조회 (PENDING, SENT, FAILED)
 */
@DisplayName("InMemoryOutboxRepository 테스트")
class InMemoryOutboxRepositoryTest {

    private InMemoryOutboxRepository outboxRepository;

    @BeforeEach
    void setUp() {
        outboxRepository = new InMemoryOutboxRepository();
    }

    // ========== Outbox 저장 ==========

    @Test
    @DisplayName("save - 새 Outbox 메시지 저장 (ID 자동 할당)")
    void testSave_NewOutbox() {
        // When
        Outbox outbox = Outbox.createOutbox(1L, 1L, "OrderCreated");
        Outbox saved = outboxRepository.save(outbox);

        // Then
        assertNotNull(saved.getMessageId());
        assertTrue(saved.getMessageId() >= 5001L);
        assertEquals("OrderCreated", saved.getMessageType());
    }

    @Test
    @DisplayName("save - 여러 메시지 저장 시 ID 증가")
    void testSave_IdSequenceIncrement() {
        // When
        Outbox outbox1 = Outbox.createOutbox(1L, 1L, "OrderCreated");
        Outbox outbox2 = Outbox.createOutbox(2L, 1L, "OrderCreated");

        Outbox saved1 = outboxRepository.save(outbox1);
        Outbox saved2 = outboxRepository.save(outbox2);

        // Then
        assertTrue(saved1.getMessageId() < saved2.getMessageId());
        assertEquals(saved1.getMessageId() + 1, saved2.getMessageId());
    }

    // ========== Outbox 조회 ==========

    @Test
    @DisplayName("findById - 저장된 메시지 조회")
    void testFindById_ExistingMessage() {
        // Given
        Outbox outbox = Outbox.createOutbox(1L, 1L, "OrderCreated");
        Outbox saved = outboxRepository.save(outbox);

        // When
        Optional<Outbox> found = outboxRepository.findById(saved.getMessageId());

        // Then
        assertTrue(found.isPresent());
        assertEquals("OrderCreated", found.get().getMessageType());
    }

    @Test
    @DisplayName("findById - 없는 메시지는 Optional.empty()")
    void testFindById_NonExistent() {
        // When
        Optional<Outbox> found = outboxRepository.findById(99999L);

        // Then
        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("findByOrderId - 주문의 모든 메시지 조회")
    void testFindByOrderId_GetAllMessagesForOrder() {
        // Given
        Outbox outbox1 = Outbox.createOutbox(100L, 1L, "OrderCreated");
        Outbox outbox2 = Outbox.createOutbox(100L, 1L, "PaymentProcessed");
        Outbox outbox3 = Outbox.createOutbox(101L, 1L, "OrderCreated");

        outboxRepository.save(outbox1);
        outboxRepository.save(outbox2);
        outboxRepository.save(outbox3);

        // When
        List<Outbox> messages = outboxRepository.findByOrderId(100L);

        // Then
        assertEquals(2, messages.size());
        assertTrue(messages.stream().allMatch(m -> 100L == m.getOrderId()));
    }

    @Test
    @DisplayName("findByOrderId - 없는 주문은 빈 리스트")
    void testFindByOrderId_EmptyList() {
        // When
        List<Outbox> messages = outboxRepository.findByOrderId(999L);

        // Then
        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    // ========== 상태별 조회 ==========

    @Test
    @DisplayName("findAllByStatus - PENDING 상태 메시지 조회")
    void testFindAllByStatus_Pending() {
        // Given
        Outbox outbox1 = Outbox.createOutbox(100L, 1L, "OrderCreated");
        Outbox outbox2 = Outbox.createOutbox(101L, 1L, "OrderCreated");
        Outbox outbox3 = Outbox.createOutbox(102L, 1L, "OrderCreated");

        Outbox saved1 = outboxRepository.save(outbox1);
        Outbox saved2 = outboxRepository.save(outbox2);
        Outbox saved3 = outboxRepository.save(outbox3);

        // When (saved3는 SENT 상태로 변경)
        saved3.markAsSent();
        outboxRepository.update(saved3);

        List<Outbox> pendingMessages = outboxRepository.findAllByStatus("PENDING");

        // Then
        assertTrue(pendingMessages.size() >= 2);
        assertTrue(pendingMessages.stream().allMatch(m -> "PENDING".equals(m.getStatus())));
    }

    @Test
    @DisplayName("findAllByStatus - SENT 상태 메시지 조회")
    void testFindAllByStatus_Sent() {
        // Given
        Outbox outbox = Outbox.createOutbox(100L, 1L, "OrderCreated");
        Outbox saved = outboxRepository.save(outbox);
        saved.markAsSent();
        outboxRepository.update(saved);

        // When
        List<Outbox> sentMessages = outboxRepository.findAllByStatus("SENT");

        // Then
        assertTrue(sentMessages.size() >= 1);
        assertTrue(sentMessages.stream().allMatch(m -> "SENT".equals(m.getStatus())));
    }

    @Test
    @DisplayName("findAllByStatus - FAILED 상태 메시지 조회")
    void testFindAllByStatus_Failed() {
        // Given
        Outbox outbox = Outbox.createOutbox(100L, 1L, "OrderCreated");
        Outbox saved = outboxRepository.save(outbox);
        saved.markAsFailed();
        outboxRepository.update(saved);

        // When
        List<Outbox> failedMessages = outboxRepository.findAllByStatus("FAILED");

        // Then
        assertTrue(failedMessages.size() >= 1);
        assertTrue(failedMessages.stream().allMatch(m -> "FAILED".equals(m.getStatus())));
    }

    @Test
    @DisplayName("findAllByStatus - 없는 상태는 빈 리스트")
    void testFindAllByStatus_EmptyList() {
        // When
        List<Outbox> messages = outboxRepository.findAllByStatus("UNKNOWN_STATUS");

        // Then
        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    // ========== Outbox 업데이트 ==========

    @Test
    @DisplayName("update - 메시지 상태 업데이트")
    void testUpdate_ChangeStatus() {
        // Given
        Outbox outbox = Outbox.createOutbox(100L, 1L, "OrderCreated");
        Outbox saved = outboxRepository.save(outbox);

        // When
        saved.markAsSent();
        Outbox updated = outboxRepository.update(saved);

        // Then
        assertEquals("SENT", updated.getStatus());
        Optional<Outbox> retrieved = outboxRepository.findById(saved.getMessageId());
        assertTrue(retrieved.isPresent());
        assertEquals("SENT", retrieved.get().getStatus());
    }

    @Test
    @DisplayName("update - 재시도 횟수 증가")
    void testUpdate_RetryCountIncrement() {
        // Given
        Outbox outbox = Outbox.createOutbox(100L, 1L, "OrderCreated");
        Outbox saved = outboxRepository.save(outbox);

        // When
        saved.markAsFailed();
        Outbox updated = outboxRepository.update(saved);

        // Then
        assertEquals(1, updated.getRetryCount());
    }

    // ========== Outbox 조회 (모든 메시지) ==========

    @Test
    @DisplayName("findAll - 모든 메시지 조회")
    void testFindAll_AllMessages() {
        // Given
        Outbox outbox1 = Outbox.createOutbox(100L, 1L, "OrderCreated");
        Outbox outbox2 = Outbox.createOutbox(101L, 1L, "OrderCreated");
        outboxRepository.save(outbox1);
        outboxRepository.save(outbox2);

        // When
        List<Outbox> allMessages = outboxRepository.findAll();

        // Then
        assertNotNull(allMessages);
        assertTrue(allMessages.size() >= 2);
    }

    // ========== 실제 사용 시나리오 ==========

    @Test
    @DisplayName("사용 시나리오 - 주문 생성 메시지 저장 및 상태 추적")
    void testScenario_OrderCreationMessageFlow() {
        // When - 1. 주문 생성 메시지 저장
        Outbox outbox = Outbox.createOutbox(100L, 1L, "OrderCreated");
        Outbox saved = outboxRepository.save(outbox);

        // Then - 초기 상태 확인
        assertEquals("PENDING", saved.getStatus());
        assertEquals(0, saved.getRetryCount());

        // When - 2. 메시지 발송 성공
        saved.markAsSent();
        Outbox updated = outboxRepository.update(saved);

        // Then - 상태 변경 확인
        assertEquals("SENT", updated.getStatus());
        Optional<Outbox> retrieved = outboxRepository.findById(saved.getMessageId());
        assertTrue(retrieved.isPresent());
        assertEquals("SENT", retrieved.get().getStatus());
    }

    @Test
    @DisplayName("사용 시나리오 - 배치 프로세스: PENDING 메시지 조회 및 처리")
    void testScenario_BatchProcessPendingMessages() {
        // Given - 여러 메시지 저장
        Outbox outbox1 = Outbox.createOutbox(100L, 1L, "OrderCreated");
        Outbox outbox2 = Outbox.createOutbox(101L, 1L, "OrderCreated");
        Outbox outbox3 = Outbox.createOutbox(102L, 1L, "OrderCreated");

        Outbox saved1 = outboxRepository.save(outbox1);
        Outbox saved2 = outboxRepository.save(outbox2);
        Outbox saved3 = outboxRepository.save(outbox3);

        // When - saved1은 이미 SENT로 변경
        saved1.markAsSent();
        outboxRepository.update(saved1);

        // When - PENDING 메시지만 조회 (배치 프로세스)
        List<Outbox> pendingMessages = outboxRepository.findAllByStatus("PENDING");

        // Then - PENDING 메시지 처리
        assertTrue(pendingMessages.size() >= 2);
        pendingMessages.forEach(msg -> {
            assertEquals("PENDING", msg.getStatus());
            msg.markAsSent();
            outboxRepository.update(msg);
        });

        // Verify all are now SENT
        List<Outbox> updatedPending = outboxRepository.findAllByStatus("PENDING");
        assertFalse(updatedPending.stream().anyMatch(m -> m.getOrderId().equals(100L)));
    }

    @Test
    @DisplayName("사용 시나리오 - 메시지 발송 실패 및 재시도")
    void testScenario_MessageSendFailureAndRetry() {
        // Given
        Outbox outbox = Outbox.createOutbox(100L, 1L, "OrderCreated");
        Outbox saved = outboxRepository.save(outbox);

        // When - 첫 번째 발송 실패
        saved.markAsFailed();
        outboxRepository.update(saved);

        // Then - 재시도 가능한 상태 확인
        Optional<Outbox> retrieved = outboxRepository.findById(saved.getMessageId());
        assertTrue(retrieved.isPresent());
        assertEquals(1, retrieved.get().getRetryCount());
        assertEquals("FAILED", retrieved.get().getStatus());

        // When - 재시도
        Outbox retryMessage = retrieved.get();
        retryMessage.resetForRetry();
        Outbox retried = outboxRepository.update(retryMessage);

        // Then - 상태 변경 확인
        assertEquals("PENDING", retried.getStatus());
    }

    @Test
    @DisplayName("사용 시나리오 - 특정 주문의 모든 이벤트 추적")
    void testScenario_TrackAllEventsForOrder() {
        // When - 주문의 여러 이벤트 저장
        Outbox orderCreated = Outbox.createOutbox(200L, 1L, "OrderCreated");
        Outbox paymentProcessed = Outbox.createOutbox(200L, 1L, "PaymentProcessed");
        Outbox inventoryReserved = Outbox.createOutbox(200L, 1L, "InventoryReserved");

        outboxRepository.save(orderCreated);
        outboxRepository.save(paymentProcessed);
        outboxRepository.save(inventoryReserved);

        // When - 주문의 모든 이벤트 조회
        List<Outbox> orderEvents = outboxRepository.findByOrderId(200L);

        // Then - 모든 이벤트 확인
        assertEquals(3, orderEvents.size());
        assertTrue(orderEvents.stream().anyMatch(e -> "OrderCreated".equals(e.getMessageType())));
        assertTrue(orderEvents.stream().anyMatch(e -> "PaymentProcessed".equals(e.getMessageType())));
        assertTrue(orderEvents.stream().anyMatch(e -> "InventoryReserved".equals(e.getMessageType())));
    }
}
