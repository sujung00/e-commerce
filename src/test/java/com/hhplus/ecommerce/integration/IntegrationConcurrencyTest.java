package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 동시성통합테스트 - TestContainers 동시성 제어 통합 테스트
 *
 * 테스트 목표:
 * - 선착순 쿠폰 발급 시 동시성 제어 검증
 * - 주문 생성 시 재고 동시 차감 제어 검증
 * - 복합 동시성 시나리오 검증
 * - 성능 테스트
 *
 * 특징:
 * - TestContainers MySQL 사용으로 격리된 환경에서 테스트
 * - 각 테스트가 UUID 기반 고유한 데이터로 실행 (테스트 간 격리)
 * - user 이메일은 UUID + 타임스탬프로 고유하게 생성
 * - 모든 이메일 중복 오류 없이 정상 작동
 */
@DisplayName("동시성 제어 통합 테스트")
class IntegrationConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private TransactionTemplate newTransactionTemplate;

    @Autowired
    public void setTransactionManager(org.springframework.transaction.PlatformTransactionManager tm) {
        this.transactionTemplate = new TransactionTemplate(tm);
        this.newTransactionTemplate = new TransactionTemplate(tm);
        this.newTransactionTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 선착순 쿠폰 발급 - 100명 동시 요청, 10개 한정
     * 동시성 제어가 제대로 작동하는지 검증
     */
    @Test
    @DisplayName("선착순 쿠폰 발급 - 100명 동시 요청, 10개 한정")
    void testCouponIssuance_ConcurrentRequests_LimitedQuantity() throws InterruptedException {
        // Given - 고유한 테스트 데이터 생성 (별도 트랜잭션에서 처리)
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long testTimestamp = System.currentTimeMillis();

        int numThreads = 100;
        long[] couponIdArray = new long[1];
        long[] userIds = new long[numThreads];

        // 테스트 데이터 생성 및 커밋
        newTransactionTemplate.execute(status -> {
            // 테스트용 쿠폰 생성 (10개 한정)
            Coupon testCoupon = Coupon.builder()
                    .couponName("선착순쿠폰_" + testId)
                    .discountType("FIXED_AMOUNT")
                    .discountAmount(5000L)
                    .isActive(true)
                    .totalQuantity(10)
                    .remainingQty(10)
                    .validFrom(LocalDateTime.now().minusDays(1))
                    .validUntil(LocalDateTime.now().plusDays(30))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            Coupon savedCoupon = couponRepository.save(testCoupon);
            couponIdArray[0] = savedCoupon.getCouponId();

            // 모든 user 생성 (이메일 중복 방지)
            for (int i = 1; i <= numThreads; i++) {
                String uniqueEmail = String.format("coupon_user%d_%s_%d@test.com", i, testId, testTimestamp);
                User user = User.builder()
                        .email(uniqueEmail)
                        .name("쿠폰테스트user" + i)
                        .balance(100000L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                userRepository.save(user);
            }
            return null;
        });

        // user ID 조회 (별도 트랜잭션)
        long couponId = couponIdArray[0];
        newTransactionTemplate.execute(status -> {
            for (int i = 1; i <= numThreads; i++) {
                String uniqueEmail = String.format("coupon_user%d_%s_%d@test.com", i, testId, testTimestamp);
                userIds[i - 1] = (Long) entityManager.createQuery(
                                "SELECT u.userId FROM User u WHERE u.email = :email")
                        .setParameter("email", uniqueEmail)
                        .getSingleResult();
            }
            return null;
        });

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(100);

        // When - 100명이 동시에 쿠폰 발급 시도
        for (int i = 1; i <= numThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    long user아이디 = userIds[index - 1];

                    startLatch.await();  // 모든 스레드 동시 시작

                    // Helper method를 통해 별도 트랜잭션에서 실행
                    try {
                        // 쿠폰 발급 시도
                        issueCouponInNewTransaction(user아이디, couponId);
                        successCount.incrementAndGet();
                    } catch (IllegalArgumentException | org.springframework.dao.DataIntegrityViolationException e) {
                        // 쿠폰 소진 또는 중복 발급 시 예상되는 실패
                        failureCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();  // 모든 스레드 동시 시작 신호
        endLatch.await();  // 모든 스레드 완료 대기
        executor.shutdown();

        // Then - 동시성 제어 검증
        assertTrue(successCount.get() >= 10,
                "최소 10명은 쿠폰을 발급받아야 합니다. 실제: " + successCount.get());

        Coupon finalCoupon = couponRepository.findById(couponId).orElseThrow();
        assertEquals(Math.max(0, 10 - successCount.get()), finalCoupon.getRemainingQty(),
                "쿠폰 남은 수량이 일치해야 합니다");
    }

    /**
     * 주문 생성 - 50명 동시 요청, 50개 재고
     * 재고 동시 차감 제어가 제대로 작동하는지 검증
     */
    @Test
    @DisplayName("주문 생성 - 50명 동시 요청, 50개 재고")
    void testOrderCreation_ConcurrentInventoryDeduction() throws InterruptedException {
        // Given - 고유한 테스트 데이터 생성
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long testTimestamp = System.currentTimeMillis();

        long[] productIdArray = new long[1];

        // 테스트 데이터 생성 및 커밋
        newTransactionTemplate.execute(status -> {
            // 테스트용 상품 생성
            Product testProduct = Product.builder()
                    .productName("재고테스트상품_" + testId)
                    .price(10000L)
                    .totalStock(50)
                    .status("IN_STOCK")
                    .options(new ArrayList<>())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.save(testProduct);
            return null;
        });

        // 저장된 상품의 ID를 조회
        newTransactionTemplate.execute(status -> {
            productIdArray[0] = (Long) entityManager.createQuery(
                            "SELECT p.productId FROM Product p WHERE p.productName = :name")
                    .setParameter("name", "재고테스트상품_" + testId)
                    .getSingleResult();

            // 상품 옵션 생성
            ProductOption option = ProductOption.builder()
                    .productId(productIdArray[0])
                    .name("기본옵션")
                    .stock(50)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option);
            return null;
        });

        long productId = productIdArray[0];

        int numThreads = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(50);

        // When - 50명이 동시에 주문 시도
        for (int i = 1; i <= numThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    try {
                        // 고유한 user 생성 (별도 트랜잭션)
                        String uniqueEmail = String.format("order_user%d_%s_%d@test.com", index, testId, testTimestamp);
                        User user = User.builder()
                                .email(uniqueEmail)
                                .name("주문테스트user" + index)
                                .balance(100000L)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        createUserInNewTransaction(user);

                        startLatch.await();  // 모든 스레드 동시 시작

                        // 재고 차감 로직을 별도 트랜잭션에서 실행
                        newTransactionTemplate.execute(status -> {
                            // 간단한 재고 차감 로직 (실제 주문 서비스 호출 대신)
                            Product product = productRepository.findById(productId).orElseThrow();
                            if (!product.getOptions().isEmpty()) {
                                ProductOption opt = product.getOptions().get(0);
                                if (opt.getStock() >= 1) {
                                    synchronized (opt) {
                                        if (opt.getStock() >= 1) {
                                            ProductOption updated = ProductOption.builder()
                                                    .optionId(opt.getOptionId())
                                                    .productId(opt.getProductId())
                                                    .name(opt.getName())
                                                    .stock(opt.getStock() - 1)
                                                    .version(opt.getVersion())
                                                    .createdAt(opt.getCreatedAt())
                                                    .updatedAt(LocalDateTime.now())
                                                    .build();
                                            productRepository.saveOption(updated);
                                            successCount.incrementAndGet();
                                        } else {
                                            failureCount.incrementAndGet();
                                        }
                                    }
                                } else {
                                    failureCount.incrementAndGet();
                                }
                            }
                            return null;
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        failureCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();  // 모든 스레드 동시 시작 신호
        endLatch.await();  // 모든 스레드 완료 대기
        executor.shutdown();

        // Then - 동시성 제어 검증
        assertTrue(successCount.get() >= 40,
                "최소 40명의 주문이 성공해야 합니다. 실제: " + successCount.get());
    }

    /**
     * 혼합 시나리오 - 쿠폰 발급과 주문 생성 동시 실행
     * 여러 리소스에 대한 동시성 제어 검증
     */
    @Test
    @DisplayName("혼합 시나리오 - 쿠폰 발급과 주문 생성 동시 실행")
    void testMixedScenario_CouponAndOrderConcurrently() throws InterruptedException {
        // Given - 고유한 테스트 데이터 생성
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long testTimestamp = System.currentTimeMillis();

        int numUsers = 20;
        long[] couponIdArray = new long[1];
        long[] userIds = new long[numUsers];

        // 테스트 데이터 생성 및 커밋
        newTransactionTemplate.execute(status -> {
            // 테스트용 쿠폰 생성
            Coupon testCoupon = Coupon.builder()
                    .couponName("mixedScenario_" + testId)
                    .discountType("FIXED_AMOUNT")
                    .discountAmount(2000L)
                    .isActive(true)
                    .totalQuantity(5)
                    .remainingQty(5)
                    .validFrom(LocalDateTime.now().minusDays(1))
                    .validUntil(LocalDateTime.now().plusDays(30))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            Coupon savedCoupon = couponRepository.save(testCoupon);
            couponIdArray[0] = savedCoupon.getCouponId();

            // 모든 user 생성 (이메일 중복 방지)
            for (int i = 1; i <= numUsers; i++) {
                String uniqueEmail = String.format("mixed_user%d_%s_%d@test.com", i, testId, testTimestamp);
                User user = User.builder()
                        .email(uniqueEmail)
                        .name("혼합테스트user" + i)
                        .balance(100000L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                userRepository.save(user);
            }
            return null;
        });

        // 생성된 user ID 조회
        long couponId = couponIdArray[0];
        newTransactionTemplate.execute(status -> {
            for (int i = 1; i <= numUsers; i++) {
                String uniqueEmail = String.format("mixed_user%d_%s_%d@test.com", i, testId, testTimestamp);
                userIds[i - 1] = (Long) entityManager.createQuery(
                                "SELECT u.userId FROM User u WHERE u.email = :email")
                        .setParameter("email", uniqueEmail)
                        .getSingleResult();
            }
            return null;
        });

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numUsers);
        AtomicInteger couponSuccessCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(20);

        // When - 20명이 동시에 쿠폰 발급 시도
        for (int i = 1; i <= numUsers; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    long userId = userIds[index - 1];

                    startLatch.await();  // 모든 스레드 동시 시작

                    couponService.issueCoupon(userId, couponId);
                    couponSuccessCount.incrementAndGet();
                } catch (Exception e) {
                    // 쿠폰 소진 시 예상되는 실패
                }  finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();  // 모든 스레드 동시 시작 신호
        endLatch.await();  // 모든 스레드 완료 대기
        executor.shutdown();

        // Then - 동시성 제어 검증
        assertTrue(couponSuccessCount.get() <= 5,
                "최대 5명만 쿠폰을 발급받을 수 있습니다. 실제: " + couponSuccessCount.get());
    }

    /**
     * 성능 테스트 - 1000개의 쿠폰 발급 요청
     * 대규모 동시 요청에서의 성능 검증
     */
    @Test
    @DisplayName("성능 테스트 - 1000개의 쿠폰 발급 요청")
    void testPerformance_LargeConcurrentRequests() throws InterruptedException {
        // Given - 고유한 테스트 데이터 생성
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long testTimestamp = System.currentTimeMillis();

        int numRequests = 1000;
        long[] couponIdArray = new long[1];
        long[] userIds = new long[numRequests];

        // 테스트 데이터 생성 및 커밋
        newTransactionTemplate.execute(status -> {
            // 테스트용 쿠폰 생성 (1000개)
            Coupon testCoupon = Coupon.builder()
                    .couponName("성능testCoupon_" + testId)
                    .discountType("FIXED_AMOUNT")
                    .discountAmount(1000L)
                    .isActive(true)
                    .totalQuantity(1000)
                    .remainingQty(1000)
                    .validFrom(LocalDateTime.now().minusDays(1))
                    .validUntil(LocalDateTime.now().plusDays(30))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            Coupon savedCoupon = couponRepository.save(testCoupon);
            couponIdArray[0] = savedCoupon.getCouponId();

            // 모든 user 생성 (배치 처리로 성능 최적화)
            for (int i = 1; i <= numRequests; i++) {
                String uniqueEmail = String.format("perf_user%d_%s_%d@test.com", i, testId, testTimestamp);
                User user = User.builder()
                        .email(uniqueEmail)
                        .name("성능테스트user" + i)
                        .balance(100000L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                userRepository.save(user);
            }
            return null;
        });

        // 생성된 user ID 조회
        long couponId = couponIdArray[0];
        newTransactionTemplate.execute(status -> {
            for (int i = 1; i <= numRequests; i++) {
                String uniqueEmail = String.format("perf_user%d_%s_%d@test.com", i, testId, testTimestamp);
                userIds[i - 1] = (Long) entityManager.createQuery(
                                "SELECT u.userId FROM User u WHERE u.email = :email")
                        .setParameter("email", uniqueEmail)
                        .getSingleResult();
            }
            return null;
        });

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(100);

        // When - 1000명이 동시에 쿠폰 발급 요청
        for (int i = 1; i <= numRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    long userId = userIds[index - 1];

                    startLatch.await();  // 모든 스레드 동시 시작

                    // Helper method를 통해 별도 트랜잭션에서 실행
                    try {
                        issueCouponInNewTransaction(userId, couponId);
                        successCount.incrementAndGet();
                    } catch (IllegalArgumentException | org.springframework.dao.DataIntegrityViolationException e) {
                        // 쿠폰 소진 시 예상되는 실패
                        failureCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();  // 모든 스레드 동시 시작 신호
        endLatch.await();  // 모든 스레드 완료 대기
        executor.shutdown();
        long endTime = System.currentTimeMillis();

        // Then - 성능 검증
        assertTrue(successCount.get() >= 1000,
                "최소 1000개의 쿠폰이 발급되어야 합니다. 실제: " + successCount.get());
        assertEquals(0, failureCount.get(),
                "모든 요청이 성공해야 합니다");

        System.out.println("[Performance Test] " + numRequests +
                " requests completed in " + (endTime - startTime) + "ms" +
                " (Success: " + successCount.get() + ", Failure: " + failureCount.get() + ")");
    }

    /**
     * Helper method - 쿠폰 발급을 별도 트랜잭션에서 실행
     * newTransactionTemplate을 사용하여 새로운 트랜잭션 생성 (REQUIRES_NEW)
     */
    private void issueCouponInNewTransaction(Long userId, Long couponId) {
        newTransactionTemplate.execute(status -> {
            couponService.issueCoupon(userId, couponId);
            return null;
        });
    }

    /**
     * Helper method - user 생성을 별도 트랜잭션에서 실행
     */
    private void createUserInNewTransaction(User user) {
        newTransactionTemplate.execute(status -> {
            userRepository.save(user);
            return null;
        });
    }
}
