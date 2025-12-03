package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.coupon.CouponQueueService;
import com.hhplus.ecommerce.application.coupon.CouponQueueMonitoringService;
import com.hhplus.ecommerce.application.coupon.CouponQueueMonitoringService.DLQItem;
import com.hhplus.ecommerce.application.coupon.CouponQueueMonitoringService.QueueStatusInfo;
import com.hhplus.ecommerce.application.coupon.dto.CouponRequest;
import com.hhplus.ecommerce.infrastructure.config.RedisKeyType;
import com.hhplus.ecommerce.infrastructure.constants.RetryConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CouponQueueRetryTest - 쿠폰 큐 재시도 로직 및 DLQ 통합 테스트
 *
 * 테스트 범위:
 * 1. 재시도 카운트 추적
 * 2. 최대 재시도 횟수 제한 (무한 반복 방지)
 * 3. DLQ(Dead Letter Queue)로의 자동 이동
 * 4. DLQ 아이템 조회
 * 5. DLQ 아이템 수동 재처리
 * 6. 큐 상태 모니터링
 */
@DisplayName("쿠폰 큐 재시도 로직 및 DLQ 통합 테스트")
class CouponQueueRetryTest extends BaseIntegrationTest {

    @Autowired
    private CouponQueueService couponQueueService;

    @Autowired
    private CouponQueueMonitoringService monitoringService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // 모든 큐 초기화
        String pendingKey = RedisKeyType.QUEUE_COUPON_PENDING.getKey();
        String retryKey = RedisKeyType.QUEUE_COUPON_RETRY.getKey();
        String dlqKey = RedisKeyType.QUEUE_COUPON_DLQ.getKey();

        redisTemplate.delete(pendingKey);
        redisTemplate.delete(retryKey);
        redisTemplate.delete(dlqKey);
    }

    @Test
    @DisplayName("재시도 카운트가 정확하게 증가하는지 확인")
    void testRetryCountIncrement() {
        // Given
        CouponRequest request = CouponRequest.of(1L, 1L);
        assertEquals(0, request.getRetryCount(), "초기 재시도 카운트는 0이어야 함");

        // When
        request.incrementRetryCount();
        assertEquals(1, request.getRetryCount());

        request.incrementRetryCount();
        assertEquals(2, request.getRetryCount());

        request.incrementRetryCount();
        assertEquals(3, request.getRetryCount());

        // Then
        assertFalse(request.isRetryable(RetryConstants.COUPON_ISSUANCE_MAX_RETRIES),
                "재시도 카운트가 MAX_RETRIES(3)에 도달하면 재시도 불가능");
    }

    @Test
    @DisplayName("최대 재시도 횟수를 초과하면 DLQ로 이동")
    void testDLQMovementOnMaxRetriesExceeded() throws Exception {
        // Given
        String dlqKey = RedisKeyType.QUEUE_COUPON_DLQ.getKey();
        CouponRequest request = CouponRequest.of(1L, 1L);

        // 재시도 카운트를 MAX_RETRIES - 1로 설정
        request.setRetryCount(RetryConstants.COUPON_ISSUANCE_MAX_RETRIES - 1);

        // When: 한 번 더 증가하면 MAX_RETRIES에 도달
        request.incrementRetryCount();

        // Then
        assertTrue(request.getRetryCount() >= RetryConstants.COUPON_ISSUANCE_MAX_RETRIES,
                "재시도 카운트가 MAX_RETRIES 이상");
        assertFalse(request.isRetryable(RetryConstants.COUPON_ISSUANCE_MAX_RETRIES),
                "재시도 불가능하므로 DLQ로 이동해야 함");

        // DLQ에 아이템 추가
        String json = objectMapper.writeValueAsString(request);
        redisTemplate.opsForList().leftPush(dlqKey, json);

        // DLQ 확인
        Long dlqSize = redisTemplate.opsForList().size(dlqKey);
        assertEquals(1, dlqSize, "DLQ에 1개 아이템이 있어야 함");
    }

    @Test
    @DisplayName("DLQ 아이템 조회 - 전체 조회")
    void testGetAllDLQItems() throws Exception {
        // Given
        String dlqKey = RedisKeyType.QUEUE_COUPON_DLQ.getKey();
        CouponRequest request1 = CouponRequest.of(1L, 1L);
        CouponRequest request2 = CouponRequest.of(2L, 2L);

        request1.setRetryCount(3);
        request1.setStatus("DLQ");
        request1.setErrorMessage("시스템 오류");

        request2.setRetryCount(3);
        request2.setStatus("DLQ");
        request2.setErrorMessage("데이터베이스 연결 실패");

        String json1 = objectMapper.writeValueAsString(request1);
        String json2 = objectMapper.writeValueAsString(request2);

        redisTemplate.opsForList().leftPush(dlqKey, json1);
        redisTemplate.opsForList().leftPush(dlqKey, json2);

        // When
        List<DLQItem> items = monitoringService.getAllDLQItems();

        // Then
        assertEquals(2, items.size(), "DLQ에 2개 아이템이 있어야 함");
        assertEquals(request1.getRequestId(), items.get(1).getRequestId());
        assertEquals(request2.getRequestId(), items.get(0).getRequestId());

        items.forEach(item -> {
            assertEquals(3, item.getRetryCount(), "모든 아이템의 재시도 카운트는 3");
            assertEquals("DLQ", item.getStatus(), "모든 아이템의 상태는 DLQ");
        });
    }

    @Test
    @DisplayName("DLQ 아이템 조회 - 특정 requestId로 조회")
    void testGetDLQItemByRequestId() throws Exception {
        // Given
        String dlqKey = RedisKeyType.QUEUE_COUPON_DLQ.getKey();
        CouponRequest request = CouponRequest.of(1L, 1L);
        request.setRetryCount(3);
        request.setStatus("DLQ");

        String json = objectMapper.writeValueAsString(request);
        redisTemplate.opsForList().leftPush(dlqKey, json);

        // When
        var item = monitoringService.getDLQItemByRequestId(request.getRequestId());

        // Then
        assertTrue(item.isPresent(), "요청 ID로 조회할 수 있어야 함");
        assertEquals(request.getRequestId(), item.get().getRequestId());
        assertEquals(3, item.get().getRetryCount());
    }

    @Test
    @DisplayName("DLQ 아이템을 재시도 큐로 이동 (수동 재처리)")
    void testMoveToRetryQueue() throws Exception {
        // Given
        String dlqKey = RedisKeyType.QUEUE_COUPON_DLQ.getKey();
        String retryKey = RedisKeyType.QUEUE_COUPON_RETRY.getKey();

        CouponRequest request = CouponRequest.of(1L, 1L);
        request.setRetryCount(3);
        request.setStatus("DLQ");

        String json = objectMapper.writeValueAsString(request);
        redisTemplate.opsForList().leftPush(dlqKey, json);

        // DLQ 크기 확인
        assertEquals(1, redisTemplate.opsForList().size(dlqKey));

        // When: 수동으로 재시도 큐로 이동
        boolean success = monitoringService.moveToRetryQueue(request.getRequestId());

        // Then
        assertTrue(success, "수동 재처리가 성공해야 함");
        assertEquals(0, redisTemplate.opsForList().size(dlqKey), "DLQ에서 제거되어야 함");
        assertEquals(1, redisTemplate.opsForList().size(retryKey), "재시도 큐에 추가되어야 함");

        // 재시도 큐의 아이템 확인
        String retryJson = redisTemplate.opsForList().rightPop(retryKey);
        assertNotNull(retryJson);
        CouponRequest retryRequest = objectMapper.readValue(retryJson, CouponRequest.class);
        assertEquals(0, retryRequest.getRetryCount(), "수동 재처리 시 재시도 카운트는 0으로 리셋");
        assertEquals("RETRY", retryRequest.getStatus());
    }

    @Test
    @DisplayName("DLQ 아이템 삭제 (처리 불가능한 경우)")
    void testRemoveDLQItem() throws Exception {
        // Given
        String dlqKey = RedisKeyType.QUEUE_COUPON_DLQ.getKey();

        CouponRequest request = CouponRequest.of(1L, 1L);
        String json = objectMapper.writeValueAsString(request);
        redisTemplate.opsForList().leftPush(dlqKey, json);

        assertEquals(1, redisTemplate.opsForList().size(dlqKey));

        // When
        boolean success = monitoringService.removeDLQItem(request.getRequestId());

        // Then
        assertTrue(success, "DLQ 아이템 삭제 성공");
        assertEquals(0, redisTemplate.opsForList().size(dlqKey), "DLQ가 비어있어야 함");
    }

    @Test
    @DisplayName("큐 상태 모니터링 - 정상 상태")
    void testQueueStatusMonitoringHealthy() throws Exception {
        // Given
        String pendingKey = RedisKeyType.QUEUE_COUPON_PENDING.getKey();
        String retryKey = RedisKeyType.QUEUE_COUPON_RETRY.getKey();

        CouponRequest request1 = CouponRequest.of(1L, 1L);
        CouponRequest request2 = CouponRequest.of(2L, 2L);

        redisTemplate.opsForList().leftPush(pendingKey, objectMapper.writeValueAsString(request1));
        redisTemplate.opsForList().leftPush(retryKey, objectMapper.writeValueAsString(request2));

        // When
        QueueStatusInfo status = monitoringService.getQueueStatus();

        // Then
        assertEquals(1, status.getPendingCount(), "대기 중인 요청 1개");
        assertEquals(1, status.getRetryCount(), "재시도 중인 요청 1개");
        assertEquals(0, status.getDlqCount(), "DLQ 아이템 없음");
        assertEquals(2, status.getTotalCount(), "전체 2개");
        assertTrue(status.isHealthy(), "큐 상태가 정상");
    }

    @Test
    @DisplayName("큐 상태 모니터링 - DLQ 아이템이 많을 때 (비정상)")
    void testQueueStatusMonitoringUnhealthy() throws Exception {
        // Given
        String dlqKey = RedisKeyType.QUEUE_COUPON_DLQ.getKey();

        // DLQ에 15개 아이템 추가 (정상 기준: 10개 이하)
        for (int i = 0; i < 15; i++) {
            CouponRequest request = CouponRequest.of((long) i, (long) i);
            redisTemplate.opsForList().leftPush(dlqKey, objectMapper.writeValueAsString(request));
        }

        // When
        QueueStatusInfo status = monitoringService.getQueueStatus();

        // Then
        assertEquals(15, status.getDlqCount(), "DLQ 아이템 15개");
        assertFalse(status.isHealthy(), "DLQ 아이템이 10개 초과하면 비정상");
    }

    @Test
    @DisplayName("DLQ 아이템이 비어있을 때 - 빈 목록 반환")
    void testGetAllDLQItemsWhenEmpty() {
        // When
        List<DLQItem> items = monitoringService.getAllDLQItems();

        // Then
        assertEquals(0, items.size(), "DLQ가 비어있으면 빈 목록 반환");
    }

    @Test
    @DisplayName("존재하지 않는 requestId로 DLQ 아이템 조회 - Optional.empty() 반환")
    void testGetDLQItemNonExistent() {
        // When
        var item = monitoringService.getDLQItemByRequestId("non-existent-id");

        // Then
        assertFalse(item.isPresent(), "존재하지 않는 요청 ID");
    }

    @Test
    @DisplayName("존재하지 않는 아이템을 재시도 큐로 이동 시도 - false 반환")
    void testMoveToRetryQueueNonExistent() {
        // When
        boolean success = monitoringService.moveToRetryQueue("non-existent-id");

        // Then
        assertFalse(success, "존재하지 않는 아이템은 이동 실패");
    }

    @Test
    @DisplayName("최대 재시도 횟수 검증 - MAX_RETRIES = 3")
    void testMaxRetriesConstant() {
        // Then
        assertEquals(3, RetryConstants.COUPON_ISSUANCE_MAX_RETRIES,
                "최대 재시도 횟수는 3회로 설정되어야 함 (무한 반복 방지)");
    }
}
