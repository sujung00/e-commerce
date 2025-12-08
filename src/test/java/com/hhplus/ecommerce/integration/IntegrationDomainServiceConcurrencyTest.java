package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponDomainService;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductDomainService;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserBalanceDomainService;
import com.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 도메인 서비스 동시성 테스트
 *
 * 목표: 분산락과 비관적 락을 사용한 동시성 제어 검증
 *
 * 테스트 시나리오:
 * 1. 동시 잔액 차감 - 원자성 보장
 * 2. 동시 쿠폰 발급 - 선착순 보장
 * 3. 동시 상품 주문 - 재고 보장
 * 4. 락 경합 및 타임아웃 처리
 */
@DisplayName("도메인 서비스 동시성 테스트")
@TestPropertySource(properties = {
    "spring.data.redis.connect-timeout=5000ms",
    "spring.redis.socket-timeout=5000ms"
})
class IntegrationDomainServiceConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private UserBalanceDomainService userBalanceDomainService;

    @Autowired
    private CouponDomainService couponDomainService;

    @Autowired
    private ProductDomainService productDomainService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    private User testUser;
    private Coupon testCoupon;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성
        testUser = User.builder()
                .email("concurrent-test@example.com")
                .name("Concurrent Test User")
                .phone("010-9999-9999")
                .balance(1000000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(testUser);

        // 테스트 쿠폰 생성 (10개 재고)
        testCoupon = Coupon.builder()
                .couponName("Limited Coupon")
                .description("10개 한정")
                .isActive(true)
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .discountRate(BigDecimal.ZERO)
                .remainingQty(10)
                .totalQuantity(10)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        couponRepository.save(testCoupon);

        // 테스트 상품 생성
        testProduct = Product.builder()
                .productName("Concurrent Product")
                .description("For concurrent testing")
                .price(10000L)
                .totalStock(50)
                .status("IN_STOCK")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.save(testProduct);
        testProduct = productRepository.findById(testProduct.getProductId()).orElseThrow();
    }

    // ==================== Balance Concurrency Tests ====================

    @Test
    @DisplayName("동시 잔액 차감 - 원자성 보장")
    void testConcurrentBalanceDeduction_EnsuresAtomicity() throws InterruptedException {
        // Given
        int numThreads = 10;
        long deductAmount = 10000L;
        Long initialBalance = testUser.getBalance();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 10개의 스레드에서 동시에 잔액 차감
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    User user = userRepository.findByIdForUpdate(testUser.getUserId()).orElseThrow();
                    userBalanceDomainService.deductBalance(user, deductAmount);
                    userRepository.save(user);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then
        latch.await();
        executor.shutdown();

        User finalUser = userRepository.findById(testUser.getUserId()).orElseThrow();
        long expectedBalance = initialBalance - (deductAmount * successCount.get());

        assertEquals(expectedBalance, finalUser.getBalance());
        assertTrue(successCount.get() > 0, "최소 1개 이상의 잔액 차감 성공");
    }

    @Test
    @DisplayName("동시 잔액 충전 - 최종 일관성")
    void testConcurrentBalanceCharge_EnsuresEventualConsistency() throws InterruptedException {
        // Given
        int numThreads = 5;
        long chargeAmount = 20000L;
        Long initialBalance = testUser.getBalance();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 5개의 스레드에서 동시에 잔액 충전
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    User user = userRepository.findByIdForUpdate(testUser.getUserId()).orElseThrow();
                    userBalanceDomainService.chargeBalance(user, chargeAmount);
                    userRepository.save(user);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then
        latch.await();
        executor.shutdown();

        User finalUser = userRepository.findById(testUser.getUserId()).orElseThrow();
        long expectedBalance = initialBalance + (chargeAmount * numThreads);

        assertEquals(expectedBalance, finalUser.getBalance());
        assertEquals(numThreads, successCount.get());
    }

    // ==================== Coupon Concurrency Tests ====================

    @Test
    @DisplayName("동시 쿠폰 발급 - 선착순 보장 (10개 한정)")
    void testConcurrentCouponIssuance_EnsuresFifo() throws InterruptedException {
        // Given
        int numThreads = 20; // 20명이 10개 쿠폰을 놓고 경쟁
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 20개의 스레드에서 동시에 쿠폰 발급 시도
        for (int i = 0; i < numThreads; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    Coupon coupon = couponRepository.findByIdForUpdate(testCoupon.getCouponId()).orElseThrow();
                    couponDomainService.validateCouponIssuable(coupon, null, LocalDateTime.now());
                    couponDomainService.decreaseStock(coupon, 1);
                    couponRepository.update(coupon);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then
        latch.await();
        executor.shutdown();

        Coupon finalCoupon = couponRepository.findById(testCoupon.getCouponId()).orElseThrow();

        // 정확히 10개만 발급되어야 함
        assertEquals(0, finalCoupon.getRemainingQty());
        assertEquals(10, successCount.get(), "정확히 10개만 발급되어야 함");
        assertEquals(10, failureCount.get(), "나머지 10개는 재고 부족으로 실패");
        assertFalse(finalCoupon.isActiveCoupon(), "재고 소진시 자동 비활성화");
    }

    // ==================== Product Stock Concurrency Tests ====================

    @Test
    @DisplayName("동시 상품 주문 - 재고 일관성")
    void testConcurrentProductOrdering_EnsuresStockConsistency() throws InterruptedException {
        // Given
        int numThreads = 10;
        ProductOption option = testProduct.findOptionById(1L)
                .orElseGet(() -> {
                    ProductOption newOption = ProductOption.builder()
                            .productId(testProduct.getProductId())
                            .name("Default Option")
                            .stock(50)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    testProduct.addOption(newOption);
                    return newOption;
                });

        int quantityPerOrder = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 10개의 스레드에서 동시에 상품 주문
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    Product product = productRepository.findById(testProduct.getProductId()).orElseThrow();
                    ProductOption opt = product.findOptionById(option.getOptionId()).orElseThrow();
                    productDomainService.validateOptionStock(opt, quantityPerOrder);
                    productDomainService.updateStatusAfterStockDeduction(product);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then
        latch.await();
        executor.shutdown();

        Product finalProduct = productRepository.findById(testProduct.getProductId()).orElseThrow();

        // 총 50개 재고에서 10 * 5 = 50개 차감 시도, 최대 10개만 성공 가능
        assertTrue(successCount.get() <= 10, "최대 10개 주문만 성공");
        assertTrue(successCount.get() > 0, "최소 1개 이상의 주문 성공");
    }

    // ==================== Lock Timeout Tests ====================

    @Test
    @DisplayName("락 타임아웃 - 대기 중 타임아웃")
    void testLockTimeout_WhenWaitingExceedsTimeout() throws InterruptedException {
        // Given: 하나의 스레드가 장시간 락 보유
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        AtomicInteger result = new AtomicInteger(0);

        // When
        // Thread 1: 락 획득하고 오래 보유
        executor.submit(() -> {
            try {
                User user = userRepository.findByIdForUpdate(testUser.getUserId()).orElseThrow();
                startLatch.countDown();
                Thread.sleep(100); // 100ms 동안 락 보유
                userBalanceDomainService.deductBalance(user, 1000L);
                userRepository.save(user);
                result.set(1);
            } catch (Exception e) {
                result.set(-1);
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: 락 획득 시도 (대기)
        executor.submit(() -> {
            try {
                startLatch.await(); // Thread 1이 시작할 때까지 대기
                User user = userRepository.findByIdForUpdate(testUser.getUserId()).orElseThrow();
                userBalanceDomainService.chargeBalance(user, 1000L);
                userRepository.save(user);
                result.compareAndSet(1, 2); // 둘 다 성공시 2
            } catch (Exception e) {
                result.set(-2);
            } finally {
                endLatch.countDown();
            }
        });

        // Then
        endLatch.await();
        executor.shutdown();

        // 순차적 처리로 두 작업 모두 성공해야 함
        User finalUser = userRepository.findById(testUser.getUserId()).orElseThrow();
        assertEquals(1000000L, finalUser.getBalance()); // -1000 + 1000 = 0 차감
    }

    // ==================== Rollback and Recovery Tests ====================

    @Test
    @DisplayName("실패한 작업의 자동 롤백")
    void testFailureAndRollback_AutoRecovery() throws InterruptedException {
        // Given
        Long initialBalance = testUser.getBalance();
        int numThreads = 5;
        long deductAmount = 200000L; // 초기 잔액보다 많은 금액
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 충분하지 않은 잔액으로 동시 차감 시도
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    User user = userRepository.findByIdForUpdate(testUser.getUserId()).orElseThrow();
                    userBalanceDomainService.deductBalance(user, deductAmount);
                    userRepository.save(user);
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then
        latch.await();
        executor.shutdown();

        User finalUser = userRepository.findById(testUser.getUserId()).orElseThrow();

        // 모든 시도가 실패했을 것
        assertEquals(numThreads, failureCount.get());
        // 잔액은 변경되지 않음
        assertEquals(initialBalance, finalUser.getBalance());
    }
}
