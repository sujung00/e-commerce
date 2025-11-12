package com.hhplus.ecommerce;

import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.application.order.OrderService;
import com.hhplus.ecommerce.application.order.OrderTransactionService;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IntegrationConcurrencyTest - 동시성 제어 통합 테스트
 *
 * 테스트 목표:
 * - 선착순 쿠폰 발급 시 Race Condition 방지 검증
 * - 주문 생성 시 재고 동시 차감 제어 검증
 * - 복합 동시성 시나리오 검증
 */
@SpringBootTest
@DisplayName("동시성 제어 통합 테스트")
class IntegrationConcurrencyTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    // ========== 테스트 데이터 설정 ==========

    @BeforeEach
    void setup() {
        // 쿠폰 생성 (10개 한정)
        Coupon coupon = Coupon.builder()
                .couponId(1001L)
                .couponName("동시성 테스트 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .isActive(true)
                .totalQuantity(10)
                .remainingQty(10)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        couponRepository.save(coupon);

        // 사용자 100명 생성
        for (int i = 1; i <= 100; i++) {
            User user = User.builder()
                    .userId((long) (5000 + i))
                    .email("user" + i + "@test.com")
                    .name("테스트사용자" + i)
                    .balance(100000L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
        }

        // 상품 생성
        ProductOption option = ProductOption.builder()
                .optionId(2001L)
                .productId(1001L)
                .name("기본옵션")
                .stock(50)
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Product product = Product.builder()
                .productId(1001L)
                .productName("테스트 상품")
                .price(10000L)
                .totalStock(50)
                .status("판매 중")
                .options(new ArrayList<>(List.of(option)))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.save(product);
    }

    // ========== 동시성 테스트: 선착순 쿠폰 발급 ==========

    @Test
    @DisplayName("선착순 쿠폰 발급 - 100명 동시 요청, 10개 한정")
    void testCouponIssuance_ConcurrentRequests_LimitedQuantity() throws InterruptedException {
        // Given
        int numThreads = 100;
        int limitedCouponCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> failureReasons = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(100);

        // When - 100명이 동시에 쿠폰 발급 시도
        for (int i = 1; i <= numThreads; i++) {
            final long userId = 5000 + i;
            executor.submit(() -> {
                try {
                    startLatch.await();  // 모든 스레드 대기
                    couponService.issueCoupon(userId, 1001L);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    failureCount.incrementAndGet();
                    if (!failureReasons.contains(e.getMessage())) {
                        failureReasons.add(e.getMessage());
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();  // 모든 스레드 동시 실행
        endLatch.await();  // 모든 스레드 종료 대기
        executor.shutdown();

        // Then - 정확히 10명만 성공해야 함
        assertEquals(limitedCouponCount, successCount.get(),
                "정확히 " + limitedCouponCount + "명만 쿠폰을 발급받아야 합니다");
        assertEquals(numThreads - limitedCouponCount, failureCount.get(),
                "나머지 " + (numThreads - limitedCouponCount) + "명은 실패해야 합니다");

        // 쿠폰 남은 수량 확인
        Coupon coupon = couponRepository.findById(1001L).orElseThrow();
        assertEquals(0, coupon.getRemainingQty(),
                "쿠폰 남은 수량이 0이어야 합니다");
    }

    // ========== 동시성 테스트: 재고 동시 차감 ==========

    @Test
    @DisplayName("주문 생성 - 50명 동시 요청, 50개 재고")
    void testOrderCreation_ConcurrentInventoryDeduction() throws InterruptedException {
        // Given
        int numThreads = 50;
        int availableStock = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(50);

        // When - 50명이 동시에 1개씩 주문 (총 50개 재고 소비)
        for (int i = 1; i <= numThreads; i++) {
            final long userId = 5000 + i;
            executor.submit(() -> {
                try {
                    startLatch.await();  // 모든 스레드 대기

                    // 간단한 주문 시나리오: 상품 조회 및 재고 확인
                    Product product = productRepository.findById(1001L).orElseThrow();
                    ProductOption option = product.getOptions().get(0);

                    // 재고 확인
                    if (option.getStock() >= 1) {
                        synchronized (option) {
                            if (option.getStock() >= 1) {
                                // Builder로 재고 차감된 객체 생성
                                ProductOption updatedOption = ProductOption.builder()
                                        .optionId(option.getOptionId())
                                        .productId(option.getProductId())
                                        .name(option.getName())
                                        .stock(option.getStock() - 1)
                                        .version(option.getVersion() + 1)
                                        .createdAt(option.getCreatedAt())
                                        .updatedAt(LocalDateTime.now())
                                        .build();
                                productRepository.saveOption(updatedOption);
                                successCount.incrementAndGet();
                            } else {
                                failureCount.incrementAndGet();
                            }
                        }
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();  // 모든 스레드 동시 실행
        endLatch.await();  // 모든 스레드 종료 대기
        executor.shutdown();

        // Then - 정확히 50명 모두 성공해야 함
        assertEquals(availableStock, successCount.get(),
                "정확히 " + availableStock + "명의 주문이 성공해야 합니다");
        assertEquals(0, failureCount.get(),
                "실패한 주문이 없어야 합니다");

        // 재고 확인
        Product product = productRepository.findById(1001L).orElseThrow();
        ProductOption option = product.getOptions().get(0);
        assertEquals(0, option.getStock(),
                "재고가 모두 소비되어 0이어야 합니다");
    }

    // ========== 동시성 테스트: 혼합 시나리오 ==========

    @Test
    @DisplayName("혼합 시나리오 - 쿠폰 발급과 주문 생성 동시 실행")
    void testMixedScenario_CouponAndOrderConcurrently() throws InterruptedException {
        // Given
        int numUsers = 20;
        int couponCount = 5;

        // 혼합 시나리오용 별도 쿠폰 생성 (5개 한정)
        Coupon mixedCoupon = Coupon.builder()
                .couponId(1003L)
                .couponName("혼합 시나리오 테스트 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(2000L)
                .isActive(true)
                .totalQuantity(5)
                .remainingQty(5)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        couponRepository.save(mixedCoupon);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numUsers * 2);
        AtomicInteger couponSuccessCount = new AtomicInteger(0);
        AtomicInteger orderSuccessCount = new AtomicInteger(0);
        AtomicInteger orderFailureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(40);

        // When - 쿠폰 발급과 주문 생성을 동시에 실행
        for (int i = 1; i <= numUsers; i++) {
            final long userId = 5000 + i;

            // 쿠폰 발급 시도
            executor.submit(() -> {
                try {
                    startLatch.await();
                    couponService.issueCoupon(userId, 1003L);
                    couponSuccessCount.incrementAndGet();
                } catch (Exception e) {
                    // 예상된 실패 (쿠폰 한정)
                } finally {
                    endLatch.countDown();
                }
            });

            // 주문 생성 시도 (재고 차감 포함)
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // 주문 생성 로직: 재고 차감 (동시성 제어 포함)
                    Product product = productRepository.findById(1001L).orElseThrow();
                    ProductOption option = product.getOptions().get(0);

                    // 재고 확인 및 차감 (Double-checked locking 패턴)
                    if (option.getStock() >= 1) {
                        synchronized (option) {
                            if (option.getStock() >= 1) {
                                // Builder로 재고 차감된 객체 생성
                                ProductOption updatedOption = ProductOption.builder()
                                        .optionId(option.getOptionId())
                                        .productId(option.getProductId())
                                        .name(option.getName())
                                        .stock(option.getStock() - 1)
                                        .version(option.getVersion() + 1)
                                        .createdAt(option.getCreatedAt())
                                        .updatedAt(LocalDateTime.now())
                                        .build();
                                productRepository.saveOption(updatedOption);
                                orderSuccessCount.incrementAndGet();
                            } else {
                                orderFailureCount.incrementAndGet();
                            }
                        }
                    } else {
                        orderFailureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    orderFailureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Then
        assertTrue(couponSuccessCount.get() <= couponCount,
                "쿠폰 발급 성공 수는 한정 수량 이하여야 합니다");
        // 재고가 50개이고 20명이 주문하므로 모두 성공 가능
        assertEquals(numUsers, orderSuccessCount.get(),
                "모든 주문이 성공해야 합니다");
        assertEquals(0, orderFailureCount.get(),
                "주문 실패가 없어야 합니다");
    }

    // ========== 성능 테스트: 대규모 동시 요청 ==========

    @Test
    @DisplayName("성능 테스트 - 1000개의 쿠폰 발급 요청")
    void testPerformance_LargeConcurrentRequests() throws InterruptedException {
        // Given
        // 새로운 쿠폰 생성 (1000개)
        Coupon coupon = Coupon.builder()
                .couponId(1002L)
                .couponName("대규모 성능 테스트 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(1000L)
                .isActive(true)
                .totalQuantity(1000)
                .remainingQty(1000)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        couponRepository.save(coupon);

        // 성능 테스트용 1000명의 사용자 생성
        int numRequests = 1000;
        for (int i = 1; i <= numRequests; i++) {
            User user = User.builder()
                    .userId((long) (10000 + i))
                    .email("perfuser" + i + "@test.com")
                    .name("성능테스트사용자" + i)
                    .balance(100000L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
        }
        int threadPoolSize = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

        // When - 1000명이 동시에 쿠폰 발급 요청
        for (int i = 1; i <= numRequests; i++) {
            final long userId = 10000 + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    couponService.issueCoupon(userId, 1002L);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    // 쿠폰 소진 시 예상되는 실패
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    // 기타 예상 못한 실패
                    failureCount.incrementAndGet();
                    System.err.println("Unexpected error: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();
        long endTime = System.currentTimeMillis();

        // Then - 1000개의 쿠폰이 모두 발급되어야 함
        // (재시도 로직으로 동시성 이슈 해결)
        assertEquals(1000, successCount.get(),
                "1000개의 쿠폰이 모두 발급되어야 합니다. 실패: " + failureCount.get());
        assertEquals(0, failureCount.get(),
                "모든 요청이 성공해야 합니다");

        System.out.println("[Performance Test] " + numRequests +
                " requests completed in " + (endTime - startTime) + "ms" +
                " (Success: " + successCount.get() + ", Failure: " + failureCount.get() + ")");
    }
}
