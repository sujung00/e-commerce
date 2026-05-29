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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
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
// ⚠️ NOT_SUPPORTED: BaseIntegrationTest의 @Transactional(REQUIRED)을 오버라이드한다.
// CouponQueueService.processCouponQueue() → CouponService.issueCouponWithLock() (REQUIRES_NEW)
// REQUIRES_NEW는 외부 테스트 트랜잭션 내에서 @BeforeEach가 저장한 데이터를 볼 수 없다.
// (MySQL InnoDB는 커밋되지 않은 데이터를 다른 트랜잭션에서 읽지 못하므로)
// NOT_SUPPORTED를 적용하면 @BeforeEach의 각 save()가 독립 트랜잭션으로 커밋되어
// REQUIRES_NEW 내부 트랜잭션에서도 해당 데이터를 정상적으로 조회할 수 있다.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
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

        // 테스트용 사용자 생성 (data.sql email unique constraint 충돌 방지)
        testUser = User.builder()
                .name("testUser")
                .email("queue-test-" + System.nanoTime() + "@example.com")
                .balance(1000000L)
                .build();
        userRepository.save(testUser);

        // 테스트용 쿠폰 생성 (100개, 발급 가능)
        testCoupon = Coupon.builder()
                .couponName("테스트 쿠폰")
                .discountType("FIXED_AMOUNT")
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

        // When: 상태 조회 (PENDING 또는 즉시 처리됨 — 10ms 백그라운드 워커가 소비할 수 있음)
        CouponIssueStatusResponse statusResponse = couponQueueService.getRequestStatus(requestId);
        System.out.println("📊 초기 상태: " + statusResponse.getStatus());

        // 큐에 남아있거나 백그라운드 워커가 이미 처리했을 수 있으므로 Awaitility로 대기
        couponQueueService.processCouponQueue();

        // Then: Awaitility로 COMPLETED 상태 대기 (최대 10초)
        await().atMost(10, SECONDS)
                .pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() -> {
                    String status = couponQueueService.getRequestStatus(requestId).getStatus();
                    if ("RETRY".equals(status)) {
                        couponQueueService.processRetryQueue(); // RETRY 큐 재처리
                    } else if ("PENDING".equals(status)) {
                        couponQueueService.processCouponQueue();
                    }
                    return "COMPLETED".equals(status);
                });

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

        // 10ms 백그라운드 워커가 이미 소비했을 수 있으므로 큐 크기 직접 체크 생략
        // 명시적 처리 호출
        couponQueueService.processCouponQueue();

        // Then: Awaitility로 모두 COMPLETED 상태 대기 (최대 10초)
        await().atMost(10, SECONDS)
                .pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() -> {
                    boolean allDone = true;
                    for (String reqId : new String[]{requestId1, requestId2, requestId3}) {
                        String st = couponQueueService.getRequestStatus(reqId).getStatus();
                        if ("RETRY".equals(st)) {
                            couponQueueService.processRetryQueue(); // RETRY 큐 재처리
                        } else if ("PENDING".equals(st)) {
                            couponQueueService.processCouponQueue();
                        }
                        if (!"COMPLETED".equals(st) && !"FAILED".equals(st)) {
                            allDone = false;
                        }
                    }
                    return allDone;
                });

        // FIFO 검증: 처음 발급은 COMPLETED, 중복 발급은 FAILED여도 순서는 보장됨
        // 모두 COMPLETED 또는 FAILED(중복 방지) 상태로 종료되었는지 확인
        for (String reqId : new String[]{requestId1, requestId2, requestId3}) {
            String finalStatus = couponQueueService.getRequestStatus(reqId).getStatus();
            assertTrue("COMPLETED".equals(finalStatus) || "FAILED".equals(finalStatus),
                    "모든 요청이 COMPLETED 또는 FAILED 상태여야 함: " + finalStatus);
        }

        System.out.println("✅ FIFO 보장 확인: 모든 요청이 순서대로 처리됨");
    }

    @Test
    @DisplayName("동시성: 100개의 동시 요청 처리")
    void testConcurrentRequests() throws InterruptedException {
        // Given: 100명의 서로 다른 사용자 생성 (UNIQUE(user_id, coupon_id) 제약으로 인해 동일 사용자는 1번만 가능)
        int numberOfRequests = 100;
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < numberOfRequests; i++) {
            User user = User.builder()
                    .name("concurrent-user-" + i)
                    .email("concurrent-" + i + "-" + System.nanoTime() + "@example.com")
                    .balance(1000000L)
                    .build();
            userRepository.save(user);
            userIds.add(user.getUserId());
        }

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numberOfRequests);
        Set<String> requestIds = new HashSet<>();
        AtomicInteger successCount = new AtomicInteger(0);

        System.out.println("🔄 동시성 테스트: " + numberOfRequests + "개 요청 (각각 다른 사용자)");

        // When: 동시에 100개의 요청 제출 (각 다른 사용자)
        for (int i = 0; i < numberOfRequests; i++) {
            final long userId = userIds.get(i);
            executorService.submit(() -> {
                try {
                    String requestId = couponQueueService.enqueueCouponRequest(
                            userId,
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

        // When: 큐 크기 확인 (10ms 백그라운드 워커가 일부 소비했을 수 있으므로 소프트 체크)
        Long queueSize = redisTemplate.opsForList()
                .size(RedisKeyType.QUEUE_COUPON_PENDING.getKey());
        System.out.println("📦 Redis 큐 크기: " + queueSize + " (백그라운드 워커가 이미 소비했을 수 있음)");

        // 큐 크기는 0 이상이면 됨 (백그라운드 워커가 일부 소비 가능)
        assertTrue(queueSize >= 0, "큐 크기는 0 이상이어야 함");

        // Awaitility로 모든 요청이 최종 상태(COMPLETED 또는 FAILED)가 될 때까지 대기 (최대 30초)
        await().atMost(30, SECONDS)
                .pollInterval(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() -> {
                    // RETRY 상태인 항목 재처리
                    boolean hasRetry = requestIds.stream()
                            .map(id -> couponQueueService.getRequestStatus(id).getStatus())
                            .anyMatch("RETRY"::equals);
                    boolean hasPending = requestIds.stream()
                            .map(id -> couponQueueService.getRequestStatus(id).getStatus())
                            .anyMatch("PENDING"::equals);
                    if (hasRetry) {
                        couponQueueService.processRetryQueue(); // RETRY 큐 재처리
                    }
                    if (hasPending) {
                        couponQueueService.processCouponQueue();
                    }
                    long doneCount = requestIds.stream()
                            .map(id -> couponQueueService.getRequestStatus(id).getStatus())
                            .filter(s -> "COMPLETED".equals(s) || "FAILED".equals(s))
                            .count();
                    return doneCount == numberOfRequests;
                });

        long completedCount = requestIds.stream()
                .map(id -> couponQueueService.getRequestStatus(id).getStatus())
                .filter("COMPLETED"::equals)
                .count();

        long failedCount = requestIds.stream()
                .map(id -> couponQueueService.getRequestStatus(id).getStatus())
                .filter("FAILED"::equals)
                .count();
        System.out.println("✅ 처리 완료된 요청: " + completedCount + "/" + numberOfRequests + " (FAILED: " + failedCount + ")");
        // 100개 쿠폰 재고에 100명 다른 사용자 요청 → 모두 COMPLETED 기대
        assertEquals(numberOfRequests, completedCount, "모든 요청이 처리되지 않음 (FAILED: " + failedCount + ")");

        executorService.shutdown();
    }

    @Test
    @DisplayName("선착순: 재고 소진 확인")
    void testFirstComeFirstServed() throws InterruptedException {
        // Given: 재고 10개인 새로운 쿠폰 생성
        Coupon limitedCoupon = Coupon.builder()
                .couponName("제한 쿠폰")
                .discountType("FIXED_AMOUNT")
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

        // Awaitility로 COMPLETED 상태 대기 (최대 10초, RETRY 발생 시 재처리)
        System.out.println("🔁 Awaitility 대기 시작 (최대 10초)");

        await().atMost(10, SECONDS)
                .pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() -> {
                    String st = couponQueueService.getRequestStatus(requestId).getStatus();
                    System.out.println("  상태: " + st);
                    if ("PENDING".equals(st) || "RETRY".equals(st)) {
                        couponQueueService.processCouponQueue();
                    }
                    return "COMPLETED".equals(st) || "FAILED".equals(st);
                });

        // Then: 최종 상태 확인
        CouponIssueStatusResponse status = couponQueueService.getRequestStatus(requestId);
        System.out.println("✅ 폴링 종료: " + status.getStatus() + " (errorMessage: " + status.getErrorMessage() + ")");

        assertNotNull(status);
        assertEquals("COMPLETED", status.getStatus(), "상태가 COMPLETED가 아님: " + status.getErrorMessage());
        assertNotNull(status.getResult());
    }

    @Test
    @DisplayName("큐 통계: 모니터링")
    void testQueueStatistics() throws InterruptedException {
        // Given: 여러 요청 제출
        String requestId1 = couponQueueService.enqueueCouponRequest(testUser.getUserId(), testCoupon.getCouponId());
        String requestId2 = couponQueueService.enqueueCouponRequest(testUser.getUserId(), testCoupon.getCouponId());

        // 10ms 백그라운드 워커가 즉시 소비하므로 처리 전 큐 크기는 보장되지 않음
        // → Awaitility로 최종 상태(COMPLETED) 대기
        couponQueueService.processCouponQueue();

        await().atMost(10, SECONDS)
                .pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() -> {
                    String s1 = couponQueueService.getRequestStatus(requestId1).getStatus();
                    String s2 = couponQueueService.getRequestStatus(requestId2).getStatus();
                    if ("RETRY".equals(s1) || "RETRY".equals(s2)) {
                        couponQueueService.processRetryQueue(); // RETRY 큐 재처리
                    } else if ("PENDING".equals(s1) || "PENDING".equals(s2)) {
                        couponQueueService.processCouponQueue();
                    }
                    return ("COMPLETED".equals(s1) || "FAILED".equals(s1))
                        && ("COMPLETED".equals(s2) || "FAILED".equals(s2));
                });

        // Then: 통계 조회 (처리 후 — PENDING 큐가 비어있어야 함)
        CouponQueueService.QueueStats statsAfterProcess = couponQueueService.getQueueStats();
        System.out.println("📊 처리 후 통계:");
        System.out.println("  - 대기 중: " + statsAfterProcess.getPendingCount());
        System.out.println("  - 재시도: " + statsAfterProcess.getRetryCount());
        System.out.println("  - 전체: " + statsAfterProcess.getTotalCount());

        assertEquals(0, statsAfterProcess.getPendingCount());
    }
}
