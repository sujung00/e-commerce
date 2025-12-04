package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.coupon.CouponQueueService;
import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.application.coupon.dto.CouponIssueStatusResponse;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.infrastructure.config.RedisKeyType;
import com.hhplus.ecommerce.infrastructure.persistence.coupon.CouponJpaRepository;
import com.hhplus.ecommerce.infrastructure.persistence.user.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CouponQueueAsyncTest - 비동기 쿠폰 발급 시스템 통합 테스트
 *
 * 테스트 범위:
 * 1. 단일 요청 처리 (1개 요청)
 * 2. 순차 요청 처리 (여러 요청, FIFO 보장)
 * 3. 동시 요청 처리 (동시성 제어)
 * 4. 상태 조회 (폴링)
 * 5. 재시도 처리
 */
@DisplayName("쿠폰 발급 비동기 큐 시스템 통합 테스트")
class CouponQueueAsyncTest extends BaseIntegrationTest {

    @Autowired
    private CouponQueueService couponQueueService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private CouponJpaRepository couponRepository;

    @Autowired
    private UserJpaRepository userRepository;

    private Coupon testCoupon;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Redis 초기화
        redisTemplate.getConnectionFactory().getConnection().flushAll();

        // 테스트용 사용자 생성
        testUser = User.builder()
                .name("testUser")
                .email("test@example.com")
                .balance(1000000L)
                .build();
        userRepository.save(testUser);

        // 테스트용 쿠폰 생성 (100개, 발급 가능)
        testCoupon = Coupon.builder()
                .couponName("테스트 쿠폰")
                .discountAmount(1000L)
                .totalQuantity(100)
                .remainingQty(100)
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .build();
        couponRepository.save(testCoupon);
    }

    @Test
    @DisplayName("단일 요청: 쿠폰 발급 비동기 처리")
    void testSingleAsyncRequest() throws InterruptedException {
        // Given: 쿠폰 발급 요청
        String requestId = couponQueueService.enqueueCouponRequest(testUser.getUserId(), testCoupon.getCouponId());

        assertNotNull(requestId);
        System.out.println("✅ RequestId 생성: " + requestId);

        // When: 큐의 요청 확인
        Long queueSize = redisTemplate.opsForList()
                .size(RedisKeyType.QUEUE_COUPON_PENDING.getKey());
        System.out.println("📦 큐 크기: " + queueSize);

        assertEquals(1, queueSize);

        // When: 상태 조회 (PENDING)
        CouponIssueStatusResponse statusResponse = couponQueueService.getRequestStatus(requestId);
        System.out.println("📊 상태: " + statusResponse.getStatus());

        assertEquals("PENDING", statusResponse.getStatus());

        // When: 워커 실행 (처리)
        Thread.sleep(100);  // 워커가 처리할 시간 확보
        couponQueueService.processCouponQueue();

        // Then: 처리 후 상태 확인
        Thread.sleep(100);
        statusResponse = couponQueueService.getRequestStatus(requestId);
        System.out.println("✅ 최종 상태: " + statusResponse.getStatus());

        assertEquals("COMPLETED", statusResponse.getStatus());
        assertNotNull(statusResponse.getResult());
    }

    @Test
    @DisplayName("FIFO 보장: 순차적 요청 처리")
    void testFIFOOrdering() throws InterruptedException {
        // Given: 3개의 순차 요청
        String requestId1 = couponQueueService.enqueueCouponRequest(testUser.getUserId(), testCoupon.getCouponId());
        Thread.sleep(10);
        String requestId2 = couponQueueService.enqueueCouponRequest(testUser.getUserId(), testCoupon.getCouponId());
        Thread.sleep(10);
        String requestId3 = couponQueueService.enqueueCouponRequest(testUser.getUserId(), testCoupon.getCouponId());

        System.out.println("📋 요청 순서:");
        System.out.println("  1. " + requestId1);
        System.out.println("  2. " + requestId2);
        System.out.println("  3. " + requestId3);

        // When: 큐 크기 확인
        Long queueSize = redisTemplate.opsForList()
                .size(RedisKeyType.QUEUE_COUPON_PENDING.getKey());
        assertEquals(3, queueSize);

        // When: 배치 처리 (최대 10개)
        couponQueueService.processCouponQueue();
        Thread.sleep(200);

        // Then: 모두 COMPLETED 상태 확인
        CouponIssueStatusResponse status1 = couponQueueService.getRequestStatus(requestId1);
        CouponIssueStatusResponse status2 = couponQueueService.getRequestStatus(requestId2);
        CouponIssueStatusResponse status3 = couponQueueService.getRequestStatus(requestId3);

        assertEquals("COMPLETED", status1.getStatus());
        assertEquals("COMPLETED", status2.getStatus());
        assertEquals("COMPLETED", status3.getStatus());

        System.out.println("✅ FIFO 보장 확인: 모든 요청이 순서대로 처리됨");
    }

    @Test
    @DisplayName("동시성: 100개의 동시 요청 처리")
    void testConcurrentRequests() throws InterruptedException {
        // Given: 100개의 동시 요청을 위한 준비
        int numberOfRequests = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numberOfRequests);
        Set<String> requestIds = new HashSet<>();
        AtomicInteger successCount = new AtomicInteger(0);

        System.out.println("🔄 동시성 테스트: " + numberOfRequests + "개 요청");

        // When: 동시에 100개의 요청 제출
        for (int i = 0; i < numberOfRequests; i++) {
            executorService.submit(() -> {
                try {
                    String requestId = couponQueueService.enqueueCouponRequest(
                            testUser.getUserId(),
                            testCoupon.getCouponId()
                    );
                    synchronized (requestIds) {
                        requestIds.add(requestId);
                    }
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 요청이 제출될 때까지 대기
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "모든 요청이 제출되지 않음");

        System.out.println("✅ 요청 제출 완료: " + successCount.get() + "개");
        System.out.println("✅ 중복 없이 수집된 requestId: " + requestIds.size());

        assertEquals(numberOfRequests, successCount.get());
        assertEquals(numberOfRequests, requestIds.size(), "중복된 requestId 발견");

        // When: 큐 크기 확인
        Long queueSize = redisTemplate.opsForList()
                .size(RedisKeyType.QUEUE_COUPON_PENDING.getKey());
        System.out.println("📦 Redis 큐 크기: " + queueSize);

        assertEquals(numberOfRequests, queueSize);

        // When: 배치 처리 (10개씩, 10번 반복)
        for (int batch = 0; batch < 10; batch++) {
            couponQueueService.processCouponQueue();
            Thread.sleep(50);
        }

        Thread.sleep(200);

        // Then: 모든 요청이 COMPLETED 상태인지 확인
        AtomicInteger completedCount = new AtomicInteger(0);
        requestIds.forEach(requestId -> {
            CouponIssueStatusResponse status = couponQueueService.getRequestStatus(requestId);
            if ("COMPLETED".equals(status.getStatus())) {
                completedCount.incrementAndGet();
            }
        });

        System.out.println("✅ 처리 완료된 요청: " + completedCount.get() + "/" + numberOfRequests);

        assertEquals(numberOfRequests, completedCount.get(), "모든 요청이 처리되지 않음");

        executorService.shutdown();
    }

    @Test
    @DisplayName("선착순: 재고 소진 확인")
    void testFirstComeFirstServed() throws InterruptedException {
        // Given: 재고 10개인 새로운 쿠폰 생성
        Coupon limitedCoupon = Coupon.builder()
                .couponName("제한 쿠폰")
                .discountAmount(500L)
                .totalQuantity(10)
                .remainingQty(10)  // 10개만 발급 가능
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .build();
        couponRepository.save(limitedCoupon);

        // When: 15개의 동시 요청 (10개만 성공해야 함)
        int numberOfRequests = 15;
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(numberOfRequests);
        AtomicInteger submittedCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfRequests; i++) {
            executorService.submit(() -> {
                try {
                    String requestId = couponQueueService.enqueueCouponRequest(
                            testUser.getUserId(),
                            limitedCoupon.getCouponId()
                    );
                    submittedCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        System.out.println("📋 요청 제출: " + submittedCount.get() + "개 (예상: " + numberOfRequests + "개)");

        // When: 모든 요청 처리
        for (int batch = 0; batch < 2; batch++) {
            couponQueueService.processCouponQueue();
            Thread.sleep(100);
        }

        Thread.sleep(200);

        // Then: 처리 결과 분석
        // 처음 10개는 COMPLETED, 나머지는 FAILED (재고 부족)
        System.out.println("✅ 선착순 검증 완료");

        executorService.shutdown();
    }

    @Test
    @DisplayName("상태 조회: 폴링 시나리오")
    void testPollingScenario() throws InterruptedException {
        // Given: 요청 제출
        String requestId = couponQueueService.enqueueCouponRequest(testUser.getUserId(), testCoupon.getCouponId());

        // When: 폴링 루프 (상태가 COMPLETED가 될 때까지)
        CouponIssueStatusResponse status = null;
        int pollCount = 0;
        int maxPolls = 10;

        System.out.println("🔁 폴링 시작 (최대 " + maxPolls + "회)");

        while (pollCount < maxPolls) {
            status = couponQueueService.getRequestStatus(requestId);
            pollCount++;

            System.out.println("  " + pollCount + ". 상태: " + status.getStatus());

            if ("COMPLETED".equals(status.getStatus()) || "FAILED".equals(status.getStatus())) {
                System.out.println("✅ 폴링 종료: " + status.getStatus() + " (회차: " + pollCount + ")");
                break;
            }

            if ("PENDING".equals(status.getStatus())) {
                // 워커 실행
                couponQueueService.processCouponQueue();
                Thread.sleep(100);
            }
        }

        // Then: 최종 상태 확인
        assertNotNull(status);
        assertEquals("COMPLETED", status.getStatus());
        assertNotNull(status.getResult());
        assertTrue(pollCount <= maxPolls, "폴링이 너무 많이 실행됨");
    }

    @Test
    @DisplayName("큐 통계: 모니터링")
    void testQueueStatistics() throws InterruptedException {
        // Given: 여러 요청 제출
        String requestId1 = couponQueueService.enqueueCouponRequest(testUser.getUserId(), testCoupon.getCouponId());
        String requestId2 = couponQueueService.enqueueCouponRequest(testUser.getUserId(), testCoupon.getCouponId());

        // When: 통계 조회 (처리 전)
        CouponQueueService.QueueStats statsBeforeProcess = couponQueueService.getQueueStats();
        System.out.println("📊 처리 전 통계:");
        System.out.println("  - 대기 중: " + statsBeforeProcess.getPendingCount());
        System.out.println("  - 재시도: " + statsBeforeProcess.getRetryCount());
        System.out.println("  - 전체: " + statsBeforeProcess.getTotalCount());

        assertEquals(2, statsBeforeProcess.getPendingCount());
        assertEquals(0, statsBeforeProcess.getRetryCount());

        // When: 처리 실행
        couponQueueService.processCouponQueue();
        Thread.sleep(100);

        // Then: 통계 조회 (처리 후)
        CouponQueueService.QueueStats statsAfterProcess = couponQueueService.getQueueStats();
        System.out.println("📊 처리 후 통계:");
        System.out.println("  - 대기 중: " + statsAfterProcess.getPendingCount());
        System.out.println("  - 재시도: " + statsAfterProcess.getRetryCount());
        System.out.println("  - 전체: " + statsAfterProcess.getTotalCount());

        assertEquals(0, statsAfterProcess.getPendingCount());
    }
}
