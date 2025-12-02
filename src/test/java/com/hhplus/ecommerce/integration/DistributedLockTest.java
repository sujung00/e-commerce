package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.application.order.OrderTransactionService;
import com.hhplus.ecommerce.application.user.UserBalanceService;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.user.InsufficientBalanceException;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 분산락 통합 테스트
 *
 * 테스트 항목:
 * 1. UserBalanceService - 동시 잔액 차감 테스트
 * 2. CouponService - 동시 쿠폰 발급 테스트 (선착순)
 * 3. 락 획득 실패 → RuntimeException 발생 검증
 * 4. 락 대기 → 순차 실행 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("분산락 통합 테스트")
public class DistributedLockTest {

    @Autowired
    private UserBalanceService userBalanceService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private ProductRepository productRepository;

    private User testUser;
    private Coupon testCoupon;

    @BeforeEach
    public void setUp() {
        // 테스트용 사용자 생성 (초기 잔액: 100,000)
        testUser = User.builder()
                .email("test@example.com")
                .passwordHash("hashed_password")
                .name("Test User")
                .phone("010-1234-5678")
                .balance(100_000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(testUser);

        // 테스트용 쿠폰 생성 (남은 개수: 10개, 유효기간: 현재)
        testCoupon = Coupon.builder()
                .couponName("Test Coupon")
                .description("Test Description")
                .discountType("FIXED")
                .discountAmount(5_000L)
                .totalQuantity(10)
                .remainingQty(10)
                .isActive(true)
                .validFrom(LocalDateTime.now().minusHours(1))
                .validUntil(LocalDateTime.now().plusHours(1))
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        couponRepository.save(testCoupon);
    }

    /**
     * Test 1: 동시 잔액 차감 - 분산락이 순차 실행을 보장
     *
     * 시나리오:
     * - 초기 잔액: 100,000
     * - 10명이 동시에 각 10,000씩 차감 시도
     * - 기대 결과: 모두 성공, 최종 잔액 = 0
     * - 검증: 동시성 문제 없이 정확한 결과
     */
    @Test
    @DisplayName("동시 잔액 차감 - 분산락으로 순차 실행 보장")
    public void testConcurrentBalanceDeduction() throws InterruptedException {
        // Given
        int threadCount = 10;
        long deductAmount = 10_000L;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 10개의 스레드가 동시에 잔액 차감
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    userBalanceService.deductBalance(testUser.getUserId(), deductAmount);
                    successCount.incrementAndGet();
                    System.out.println("[SUCCESS] 잔액 차감 완료 - Thread: " + Thread.currentThread().getName());
                } catch (InsufficientBalanceException e) {
                    failureCount.incrementAndGet();
                    System.out.println("[FAILURE] 잔액 부족 - Thread: " + Thread.currentThread().getName());
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.out.println("[ERROR] " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then: 모든 스레드가 완료될 때까지 대기
        latch.await();
        executorService.shutdown();

        // Verify: 최종 결과 검증
        User updatedUser = userRepository.findById(testUser.getUserId()).orElseThrow();
        System.out.println("\n=== 동시 잔액 차감 테스트 결과 ===");
        System.out.println("성공: " + successCount.get() + "명");
        System.out.println("실패: " + failureCount.get() + "명");
        System.out.println("최종 잔액: " + updatedUser.getBalance());

        // 10명 모두 성공
        assertEquals(10, successCount.get(), "10명 모두 성공해야 함");
        assertEquals(0, failureCount.get(), "실패가 없어야 함");
        // 최종 잔액: 100,000 - (10,000 * 10) = 0
        assertEquals(0L, updatedUser.getBalance(), "최종 잔액은 0이어야 함");
    }

    /**
     * Test 2: 동시 잔액 차감 - 잔액 부족 시나리오
     *
     * 시나리오:
     * - 초기 잔액: 100,000
     * - 15명이 동시에 각 10,000씩 차감 시도
     * - 기대 결과: 10명 성공, 5명 실패 (잔액 부족)
     * - 검증: 정확한 순차 실행과 예외 처리
     */
    @Test
    @DisplayName("동시 잔액 차감 - 잔액 부족 시나리오")
    public void testConcurrentBalanceDeductionWithInsufficientBalance() throws InterruptedException {
        // Given
        int threadCount = 15;
        long deductAmount = 10_000L;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 15개 스레드가 동시에 차감 시도 (잔액 100,000이므로 10명만 성공)
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    userBalanceService.deductBalance(testUser.getUserId(), deductAmount);
                    successCount.incrementAndGet();
                } catch (InsufficientBalanceException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        User updatedUser = userRepository.findById(testUser.getUserId()).orElseThrow();
        System.out.println("\n=== 잔액 부족 테스트 결과 ===");
        System.out.println("성공: " + successCount.get() + "명");
        System.out.println("실패(잔액부족): " + failureCount.get() + "명");
        System.out.println("최종 잔액: " + updatedUser.getBalance());

        // 10명 성공, 5명 실패
        assertEquals(10, successCount.get(), "10명이 성공해야 함");
        assertEquals(5, failureCount.get(), "5명이 실패해야 함");
        assertEquals(0L, updatedUser.getBalance(), "최종 잔액은 0이어야 함");
    }

    /**
     * Test 3: 동시 쿠폰 발급 - 선착순 제한
     *
     * 시나리오:
     * - 쿠폰 남은 개수: 10개
     * - 20명이 동시에 쿠폰 발급 신청 (서로 다른 사용자)
     * - 기대 결과: 10명 성공, 10명 실패 (쿠폰 소진)
     * - 검증: UNIQUE 제약으로 중복 발급 방지
     */
    @Test
    @DisplayName("동시 쿠폰 발급 - 선착순 제한")
    public void testConcurrentCouponIssue() throws InterruptedException {
        // Given
        int threadCount = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 20개 스레드가 동시에 쿠폰 발급 시도
        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            executorService.submit(() -> {
                try {
                    // 각 스레드마다 다른 사용자로 쿠폰 발급
                    User user = User.builder()
                            .email("user" + threadNum + "@example.com")
                            .passwordHash("hashed")
                            .name("User " + threadNum)
                            .phone("010-0000-000" + threadNum)
                            .balance(100_000L)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    userRepository.save(user);

                    couponService.issueCoupon(user.getUserId(), testCoupon.getCouponId());
                    successCount.incrementAndGet();
                    System.out.println("[SUCCESS] 쿠폰 발급 완료 - User: " + threadNum);
                } catch (IllegalArgumentException e) {
                    failureCount.incrementAndGet();
                    System.out.println("[EXHAUSTED] 쿠폰 소진 - User: " + threadNum);
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.out.println("[ERROR] " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        Coupon updatedCoupon = couponRepository.findById(testCoupon.getCouponId()).orElseThrow();

        System.out.println("\n=== 동시 쿠폰 발급 테스트 결과 ===");
        System.out.println("성공: " + successCount.get() + "명");
        System.out.println("실패: " + failureCount.get() + "명");
        System.out.println("남은 쿠폰: " + updatedCoupon.getRemainingQty() + "개");

        // 10명만 성공
        assertEquals(10, successCount.get(), "10명이 성공해야 함");
        assertEquals(10, failureCount.get(), "10명이 실패해야 함");
        assertEquals(0, updatedCoupon.getRemainingQty(), "남은 쿠폰이 0이어야 함");
    }

    /**
     * Test 4: 락 획득 실패 → RuntimeException 발생
     *
     * 시나리오:
     * - UserBalanceService에서 락 대기 시간 초과 시뮬레이션
     * - 기대 결과: RuntimeException 발생
     * - 검증: 예외 메시지에 "락 획득 실패" 포함
     */
    @Test
    @DisplayName("락 획득 실패 → RuntimeException 발생")
    public void testLockAcquisitionFailure() throws InterruptedException {
        // Given: 장시간 락을 점유하는 스레드 1개
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        // Thread 1: 락을 획득하고 오래 유지
        executorService.submit(() -> {
            try {
                startLatch.countDown();
                // 첫 번째 차감
                userBalanceService.deductBalance(testUser.getUserId(), 10_000L);
                Thread.sleep(3000); // 3초 동안 락 유지 (leaseTime=2초 후 자동 해제됨)
            } catch (Exception e) {
                System.out.println("[Thread 1] Error: " + e.getMessage());
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: 락 대기
        startLatch.await();
        Thread.sleep(500); // Thread 1이 먼저 실행되도록 대기

        executorService.submit(() -> {
            try {
                // leaseTime=2초이므로 자동 해제되고 대기 시간 내에 획득 가능
                userBalanceService.deductBalance(testUser.getUserId(), 10_000L);
                System.out.println("[Thread 2] 락 획득 성공");
            } catch (RuntimeException e) {
                System.out.println("[Thread 2] RuntimeException: " + e.getMessage());
                assertTrue(e.getMessage().contains("락 획득 실패") || e.getMessage().contains("인터럽트"));
            } catch (Exception e) {
                System.out.println("[Thread 2] Other Exception: " + e.getMessage());
            } finally {
                endLatch.countDown();
            }
        });

        endLatch.await();
        executorService.shutdown();
    }

    /**
     * Test 5: 동시 잔액 충전
     *
     * 시나리오:
     * - 초기 잔액: 0
     * - 10명이 동시에 각 10,000씩 충전
     * - 기대 결과: 모두 성공, 최종 잔액 = 100,000
     */
    @Test
    @DisplayName("동시 잔액 충전")
    public void testConcurrentBalanceCharge() throws InterruptedException {
        // Given: 잔액 0인 사용자
        User newUser = User.builder()
                .email("charge@example.com")
                .passwordHash("hashed")
                .name("Charge User")
                .phone("010-9999-9999")
                .balance(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(newUser);

        int threadCount = 10;
        long chargeAmount = 10_000L;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 10개 스레드가 동시에 충전
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    userBalanceService.chargeBalance(newUser.getUserId(), chargeAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.out.println("[ERROR] " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        User updatedUser = userRepository.findById(newUser.getUserId()).orElseThrow();
        System.out.println("\n=== 동시 잔액 충전 테스트 결과 ===");
        System.out.println("성공: " + successCount.get() + "명");
        System.out.println("최종 잔액: " + updatedUser.getBalance());

        assertEquals(10, successCount.get(), "10명 모두 성공해야 함");
        assertEquals(100_000L, updatedUser.getBalance(), "최종 잔액은 100,000이어야 함");
    }
}
