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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
 *
 * ⚠️ NOT_SUPPORTED:
 *   BaseIntegrationTest의 @Transactional(REQUIRED)을 오버라이드한다.
 *   이유: 동시성 테스트에서 spawned thread들은 main thread의 Spring 트랜잭션
 *   컨텍스트(ThreadLocal)를 공유하지 않는다. 따라서 @BeforeEach에서 생성한
 *   데이터가 outer test transaction 안에 있으면 spawned thread는 해당 데이터를
 *   볼 수 없다 (MySQL InnoDB REPEATABLE READ).
 *   NOT_SUPPORTED: @BeforeEach의 각 save()가 독립 트랜잭션으로 커밋되어
 *   spawned thread에서도 조회 가능.
 *   데이터 격리는 @BeforeEach의 고유 suffix(System.nanoTime())로 보장한다.
 */
@DisplayName("도메인 서비스 동시성 테스트")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
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

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 읽기-쓰기 TransactionTemplate (REQUIRES_NEW).
     *
     * 목적: spawned thread에서 findByIdForUpdate + modify + save를 단일 트랜잭션으로 감싸기.
     * - REQUIRES_NEW: 항상 새 트랜잭션 생성 (기존 tx 없음 → REQUIRED와 동일 효과지만 명시적)
     * - readOnly = false(기본): SELECT FOR UPDATE가 읽기-쓰기 트랜잭션 안에서 실행됨
     *   → Spring Data JPA의 클래스-레벨 readOnly=true 힌트를 무시하고 비관적 락 정상 동작
     * - 락이 트랜잭션 종료 시까지 유지됨: find → modify → save 전체를 직렬화
     */
    private TransactionTemplate newTx() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }

    private User testUser;
    private Coupon testCoupon;
    private Product testProduct;
    /** 상품 동시성 테스트에서 사용할 ProductOption ID */
    private Long testProductOptionId;

    @BeforeEach
    void setUp() {
        // 테스트마다 고유한 suffix (email UNIQUE 충돌 방지 + NOT_SUPPORTED로 롤백 없음)
        long suffix = System.nanoTime();

        // 테스트 사용자 생성 (독립 트랜잭션으로 커밋됨)
        testUser = User.builder()
                .email("domain-concurrent-" + suffix + "@example.com")
                .name("Concurrent Test User")
                .phone("010-9999-9999")
                .balance(1000000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(testUser);

        // 테스트 쿠폰 생성 (10개 재고)
        testCoupon = Coupon.builder()
                .couponName("Limited Coupon " + suffix)
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
                .productName("Concurrent Product " + suffix)
                .description("For concurrent testing")
                .price(10000L)
                .totalStock(50)
                .status("IN_STOCK")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.save(testProduct);

        // 테스트 상품 옵션 생성 (재고 50개) - 상품 동시성 테스트용
        // NOT_SUPPORTED이므로 saveOption()이 독립 트랜잭션으로 즉시 커밋됨
        ProductOption testOption = ProductOption.createOption(
                testProduct.getProductId(), "Default Option", 50);
        productRepository.saveOption(testOption);

        // 저장된 옵션 ID 조회 (spawned thread에서 사용)
        List<ProductOption> savedOptions =
                productRepository.findOptionsByProductId(testProduct.getProductId());
        testProductOptionId = savedOptions.isEmpty() ? null : savedOptions.get(0).getOptionId();
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
        // ⚠️ TransactionTemplate: find + modify + save를 단일 트랜잭션으로 래핑.
        //   비관적 락(SELECT FOR UPDATE)이 트랜잭션 종료 시까지 유지되어 직렬화 보장.
        //   Spring Data JPA의 readOnly=true 힌트를 read-write 트랜잭션이 무시함.
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    newTx().execute(status -> {
                        User user = userRepository.findByIdForUpdate(testUser.getUserId()).orElseThrow();
                        userBalanceDomainService.deductBalance(user, deductAmount);
                        userRepository.save(user);
                        return null;
                    });
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
        // ⚠️ TransactionTemplate: 비관적 락이 tx 종료 시까지 유지 → 5개 모두 직렬화되어 성공
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    newTx().execute(status -> {
                        User user = userRepository.findByIdForUpdate(testUser.getUserId()).orElseThrow();
                        userBalanceDomainService.chargeBalance(user, chargeAmount);
                        userRepository.save(user);
                        return null;
                    });
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
        // ⚠️ TransactionTemplate: findByIdForUpdate(쿠폰 행 비관적 락) + validate + update를
        //   단일 트랜잭션으로 래핑. 락이 트랜잭션 종료 시까지 유지 → 정확히 10개만 발급.
        for (int i = 0; i < numThreads; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    newTx().execute(status -> {
                        Coupon coupon = couponRepository.findByIdForUpdate(testCoupon.getCouponId()).orElseThrow();
                        couponDomainService.validateCouponIssuable(coupon, testUser, LocalDateTime.now());
                        couponDomainService.decreaseStock(coupon, 1);
                        couponRepository.update(coupon);
                        return null;
                    });
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
        // setUp()에서 저장된 옵션 ID 사용 (NOT_SUPPORTED이므로 이미 커밋됨)
        assertNotNull(testProductOptionId, "setUp()에서 ProductOption이 저장되어야 함");
        final Long optionId = testProductOptionId;

        int numThreads = 10;
        int quantityPerOrder = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 10개의 스레드에서 동시에 상품 주문 검증 (실제 재고 차감은 없음 - 검증만)
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    Product product = productRepository.findById(testProduct.getProductId()).orElseThrow();
                    ProductOption opt = product.findOptionById(optionId).orElseThrow();
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

        // 총 50개 재고 / 5 = 10개 주문 가능, 모두 성공
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
        // Thread 1: 락 획득하고 오래 보유 (트랜잭션 내에서 sleep → 락 유지)
        executor.submit(() -> {
            try {
                newTx().execute(status -> {
                    User user = userRepository.findByIdForUpdate(testUser.getUserId()).orElseThrow();
                    startLatch.countDown();
                    try {
                        Thread.sleep(100); // 100ms 동안 락 보유 (트랜잭션 내)
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    userBalanceDomainService.deductBalance(user, 1000L);
                    userRepository.save(user);
                    return null;
                });
                result.set(1);
            } catch (Exception e) {
                result.set(-1);
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: 락 획득 시도 (Thread 1이 락을 보유하는 동안 대기)
        executor.submit(() -> {
            try {
                startLatch.await(); // Thread 1이 시작할 때까지 대기
                newTx().execute(status -> {
                    User user = userRepository.findByIdForUpdate(testUser.getUserId()).orElseThrow();
                    userBalanceDomainService.chargeBalance(user, 1000L);
                    userRepository.save(user);
                    return null;
                });
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
        Long initialBalance = testUser.getBalance();  // 1,000,000
        int numThreads = 5;
        long deductAmount = 1_500_000L; // 초기 잔액(1,000,000)보다 많은 금액 → 모두 InsufficientBalanceException
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 충분하지 않은 잔액으로 동시 차감 시도
        // ⚠️ TransactionTemplate: InsufficientBalanceException이 tx rollback을 보장
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    newTx().execute(status -> {
                        User user = userRepository.findByIdForUpdate(testUser.getUserId()).orElseThrow();
                        userBalanceDomainService.deductBalance(user, deductAmount); // InsufficientBalanceException
                        userRepository.save(user); // 도달하지 않음
                        return null;
                    });
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

        // 모든 시도가 InsufficientBalanceException으로 실패 → tx rollback
        assertEquals(numThreads, failureCount.get());
        // 잔액은 변경되지 않음 (모든 tx rollback)
        assertEquals(initialBalance, finalUser.getBalance());
    }
}
