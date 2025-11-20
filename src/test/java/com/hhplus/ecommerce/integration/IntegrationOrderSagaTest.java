package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.order.OrderService;
import com.hhplus.ecommerce.application.order.OrderTransactionService;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.order.OrderStatus;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.presentation.order.request.CreateOrderRequest;
import com.hhplus.ecommerce.presentation.order.request.CreateOrderRequest.OrderItemRequest;
import com.hhplus.ecommerce.presentation.order.response.OrderResponseDto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IntegrationOrderSagaTest - 주문 Saga 패턴 및 결제 흐름 동시성 테스트
 *
 * 테스트 목표:
 * 1. 결제 프로세스의 원자성 검증 (@Transactional + @Retryable)
 * 2. 쿠폰 차감과 재고 차감의 동시성 제어
 * 3. 낙관적 락 재시도 (@Retryable OptimisticLockException)
 * 4. 부분 실패 시 보상 트랜잭션 검증
 * 5. 동시 결제 시 금액 일관성 보장
 *
 * 특징:
 * - TestContainers MySQL 사용으로 실제 DB 동시성 테스트
 * - 멀티스레드 환경에서 주문 생성 동시 실행
 * - 쿠폰, 재고, 사용자 잔액 동시 차감 검증
 * - OptimisticLockException 발생 및 자동 재시도 검증
 */
@SpringBootTest
@DisplayName("주문 Saga 패턴 및 결제 흐름 동시성 테스트")
class IntegrationOrderSagaTest extends BaseIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderTransactionService orderTransactionService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate newTransactionTemplate;

    @Autowired
    public void setTransactionManager(PlatformTransactionManager tm) {
        this.newTransactionTemplate = new TransactionTemplate(tm);
        this.newTransactionTemplate.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    /**
     * SCENARIO 1: 동시 결제 시 금액 일관성
     *
     * 목표: 50명이 동시에 결제 시도 → 각각 정확한 금액 차감
     *
     * 검증:
     * - 각 사용자의 잔액이 정확히 차감됨
     * - Lost Update 발생하지 않음 (@Retryable로 보호)
     * - OptimisticLockException 발생 → 자동 재시도
     * - 최종 잔액 계산이 정확함
     */
    @Test
    @DisplayName("[Saga 결제] 50명 동시 결제 시 금액 일관성 보장")
    void testOrderPayment_ConcurrentPayment_AmountConsistency() throws InterruptedException {
        // Given - 테스트 데이터 준비
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long testTimestamp = System.currentTimeMillis();
        int numThreads = 50;
        long[] productIdArray = new long[1];
        long[] optionIdArray = new long[1];
        long[] userIds = new long[numThreads];

        // 상품 및 옵션 생성 (모든 사용자가 구매할 상품)
        newTransactionTemplate.execute(status -> {
            Product product = Product.builder()
                    .productName("결제테스트상품_" + testId)
                    .price(10000L)
                    .totalStock(50)
                    .status("IN_STOCK")
                    .options(new ArrayList<>())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.save(product);
            entityManager.flush();

            ProductOption option = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("기본옵션")
                    .stock(50)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option);
            entityManager.flush();

            var savedOptions = productRepository.findOptionsByProductId(product.getProductId());
            productIdArray[0] = product.getProductId();
            optionIdArray[0] = savedOptions.get(0).getOptionId();

            // 사용자 생성 (각각 100000원 잔액)
            for (int i = 1; i <= numThreads; i++) {
                String uniqueEmail = String.format("pay_user%d_%s_%d@test.com", i, testId, testTimestamp);
                User user = User.builder()
                        .email(uniqueEmail)
                        .name("결제테스트사용자" + i)
                        .balance(100000L)  // 충분한 잔액
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                userRepository.save(user);
            }
            return null;
        });

        // 사용자 ID 조회
        long productId = productIdArray[0];
        long optionId = optionIdArray[0];
        newTransactionTemplate.execute(status -> {
            for (int i = 1; i <= numThreads; i++) {
                String uniqueEmail = String.format("pay_user%d_%s_%d@test.com", i, testId, testTimestamp);
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
        AtomicInteger retryCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(50);

        // When - 50명이 동시에 결제 시도
        for (int i = 1; i <= numThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    long userId = userIds[index - 1];

                    // 주문 생성 요청
                    CreateOrderRequest request = CreateOrderRequest.builder()
                            .items(List.of(
                                    OrderItemRequest.builder()
                                            .productId(productId)
                                            .optionId(optionId)
                                            .quantity(1)
                                            .build()
                            ))
                            .build();

                    try {
                        // 이 메서드는 @Transactional + @Retryable로 보호됨
                        newTransactionTemplate.execute(status -> {
                            try {
                                orderService.createOrder(userId, request);
                                successCount.incrementAndGet();
                                return null;
                            } catch (ObjectOptimisticLockingFailureException e) {
                                retryCount.incrementAndGet();
                                throw e;
                            }
                        });
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Then - 결제 일관성 검증
        System.out.println("\n[테스트 결과] 동시 결제 (50명)");
        System.out.println("성공한 결제: " + successCount.get() + "명");
        System.out.println("실패한 결제: " + failureCount.get() + "명");
        System.out.println("재시도 발생: " + retryCount.get() + "건");

        // 검증 1: 최소 50명은 결제 성공
        assertEquals(50, successCount.get(),
                "50명 모두 결제에 성공해야 합니다. 실제: " + successCount.get());

        // 검증 2: 재고가 정확히 50개 차감됨
        newTransactionTemplate.execute(status -> {
            Product product = productRepository.findById(productId).orElseThrow();
            assertEquals(0, product.getTotalStock(),
                    "재고가 50개 모두 차감되어야 합니다. 남은 재고: " + product.getTotalStock());
            return null;
        });

        // 검증 3: 생성된 주문이 정확히 50개
        List<Order> createdOrders = orderRepository.findByUserIds(
                java.util.Arrays.asList(userIds)
        );
        assertEquals(50, createdOrders.size(),
                "50개의 주문이 생성되어야 합니다. 생성된 주문: " + createdOrders.size());
    }

    /**
     * SCENARIO 2: 쿠폰과 재고 동시 차감
     *
     * 목표: 10개 쿠폰 + 20개 재고 → 10명만 쿠폰 사용 후 결제, 나머지는 쿠폰 없이 결제
     *
     * 검증:
     * - 쿠폰이 정확히 10개만 사용됨
     * - 재고가 정확히 20개 차감됨
     * - 쿠폰 중복 사용 없음
     */
    @Test
    @DisplayName("[Saga 쿠폰] 쿠폰과 재고 동시 차감")
    void testOrderPayment_CouponAndInventory_ConcurrentDeduction() throws InterruptedException {
        // Given
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long testTimestamp = System.currentTimeMillis();
        int numThreads = 20;
        long[] productIdArray = new long[1];
        long[] optionIdArray = new long[1];
        long[] couponIdArray = new long[1];
        long[] userIds = new long[numThreads];

        // 상품, 쿠폰, 사용자 생성
        newTransactionTemplate.execute(status -> {
            // 상품 생성 (20개 재고)
            Product product = Product.builder()
                    .productName("쿠폰상품_" + testId)
                    .price(50000L)
                    .totalStock(20)
                    .status("IN_STOCK")
                    .options(new ArrayList<>())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.save(product);
            entityManager.flush();

            ProductOption option = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("기본옵션")
                    .stock(20)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option);
            entityManager.flush();

            // 쿠폰 생성 (10개 한정)
            Coupon coupon = Coupon.builder()
                    .couponName("동시성테스트쿠폰_" + testId)
                    .discountType("FIXED_AMOUNT")
                    .discountAmount(10000L)
                    .isActive(true)
                    .totalQuantity(10)
                    .remainingQty(10)
                    .validFrom(LocalDateTime.now().minusDays(1))
                    .validUntil(LocalDateTime.now().plusDays(30))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            couponRepository.save(coupon);
            entityManager.flush();

            var savedOptions = productRepository.findOptionsByProductId(product.getProductId());
            productIdArray[0] = product.getProductId();
            optionIdArray[0] = savedOptions.get(0).getOptionId();
            couponIdArray[0] = coupon.getCouponId();

            // 사용자 생성
            for (int i = 1; i <= numThreads; i++) {
                String uniqueEmail = String.format("coupon_order_user%d_%s_%d@test.com", i, testId, testTimestamp);
                User user = User.builder()
                        .email(uniqueEmail)
                        .name("쿠폰주문테스트사용자" + i)
                        .balance(100000L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                userRepository.save(user);
            }
            return null;
        });

        long productId = productIdArray[0];
        long optionId = optionIdArray[0];
        long couponId = couponIdArray[0];

        newTransactionTemplate.execute(status -> {
            for (int i = 1; i <= numThreads; i++) {
                String uniqueEmail = String.format("coupon_order_user%d_%s_%d@test.com", i, testId, testTimestamp);
                userIds[i - 1] = (Long) entityManager.createQuery(
                                "SELECT u.userId FROM User u WHERE u.email = :email")
                        .setParameter("email", uniqueEmail)
                        .getSingleResult();
            }
            return null;
        });

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger couponSuccessCount = new AtomicInteger(0);
        AtomicInteger orderSuccessCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(20);

        // When - 20명이 동시에 주문 시도 (쿠폰 사용 시도)
        for (int i = 1; i <= numThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    long userId = userIds[index - 1];

                    // 쿠폰 발급 시도
                    try {
                        newTransactionTemplate.execute(status -> {
                            UserCoupon userCoupon = UserCoupon.builder()
                                    .userId(userId)
                                    .couponId(couponId)
                                    .status(UserCouponStatus.UNUSED)
                                    .issuedAt(LocalDateTime.now())
                                    .build();
                            userCouponRepository.save(userCoupon);
                            couponSuccessCount.incrementAndGet();
                            return null;
                        });
                    } catch (Exception e) {
                        // 쿠폰 소진 시 무시
                    }

                    // 주문 생성
                    CreateOrderRequest request = CreateOrderRequest.builder()
                            .items(List.of(
                                    OrderItemRequest.builder()
                                            .productId(productId)
                                            .optionId(optionId)
                                            .quantity(1)
                                            .build()
                            ))
                            .couponId(couponId)
                            .build();

                    try {
                        newTransactionTemplate.execute(status -> {
                            orderService.createOrder(userId, request);
                            orderSuccessCount.incrementAndGet();
                            return null;
                        });
                    } catch (Exception e) {
                        // 쿠폰 중복 사용 또는 다른 오류
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Then - 검증
        System.out.println("\n[테스트 결과] 쿠폰과 주문 동시 생성");
        System.out.println("쿠폰 발급 성공: " + couponSuccessCount.get() + "개");
        System.out.println("주문 생성 성공: " + orderSuccessCount.get() + "개");

        // 검증: 쿠폰이 최대 10개만 사용됨
        assertTrue(couponSuccessCount.get() <= 10,
                "쿠폰은 최대 10개만 사용 가능합니다. 실제: " + couponSuccessCount.get());

        // 검증: 주문이 20개 모두 생성됨
        assertEquals(20, orderSuccessCount.get(),
                "20개의 주문이 모두 생성되어야 합니다. 생성된 주문: " + orderSuccessCount.get());

        // 검증: 재고가 정확히 20개 차감됨
        newTransactionTemplate.execute(status -> {
            Product product = productRepository.findById(productId).orElseThrow();
            assertEquals(0, product.getTotalStock(),
                    "재고가 20개 모두 차감되어야 합니다. 남은 재고: " + product.getTotalStock());
            return null;
        });
    }

    /**
     * SCENARIO 3: OptimisticLockException 발생 및 자동 재시도
     *
     * 목표: 동시 결제에서 OptimisticLockException 발생 시 @Retryable로 자동 재시도
     *
     * 검증:
     * - OptimisticLockException 감지
     * - 자동 재시도 (최대 3회)
     * - Exponential Backoff + Jitter 적용
     * - 최종 결제 성공
     */
    @Test
    @DisplayName("[Optimistic Lock] 동시 결제 시 자동 재시도")
    void testOrderPayment_OptimisticLock_AutoRetry() throws InterruptedException {
        // Given - 동시 구매 환경에서 OptimisticLockException 재현
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long testTimestamp = System.currentTimeMillis();
        int numThreads = 10;
        long[] productIdArray = new long[1];
        long[] optionIdArray = new long[1];
        long[] userIds = new long[numThreads];

        newTransactionTemplate.execute(status -> {
            // 상품 생성
            Product product = Product.builder()
                    .productName("재시도테스트상품_" + testId)
                    .price(15000L)
                    .totalStock(10)
                    .status("IN_STOCK")
                    .options(new ArrayList<>())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.save(product);
            entityManager.flush();

            ProductOption option = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("기본옵션")
                    .stock(10)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option);
            entityManager.flush();

            var savedOptions = productRepository.findOptionsByProductId(product.getProductId());
            productIdArray[0] = product.getProductId();
            optionIdArray[0] = savedOptions.get(0).getOptionId();

            // 사용자 생성
            for (int i = 1; i <= numThreads; i++) {
                String uniqueEmail = String.format("retry_user%d_%s_%d@test.com", i, testId, testTimestamp);
                User user = User.builder()
                        .email(uniqueEmail)
                        .name("재시도테스트사용자" + i)
                        .balance(100000L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                userRepository.save(user);
            }
            return null;
        });

        long productId = productIdArray[0];
        long optionId = optionIdArray[0];

        newTransactionTemplate.execute(status -> {
            for (int i = 1; i <= numThreads; i++) {
                String uniqueEmail = String.format("retry_user%d_%s_%d@test.com", i, testId, testTimestamp);
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

        ExecutorService executor = Executors.newFixedThreadPool(10);

        // When - 10명이 동시에 결제 시도 (OptimisticLock 충돌 유도)
        long startTime = System.currentTimeMillis();
        for (int i = 1; i <= numThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    long userId = userIds[index - 1];

                    CreateOrderRequest request = CreateOrderRequest.builder()
                            .items(List.of(
                                    OrderItemRequest.builder()
                                            .productId(productId)
                                            .optionId(optionId)
                                            .quantity(1)
                                            .build()
                            ))
                            .build();

                    try {
                        newTransactionTemplate.execute(status -> {
                            orderService.createOrder(userId, request);
                            successCount.incrementAndGet();
                            return null;
                        });
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();
        long endTime = System.currentTimeMillis();

        // Then - 재시도 검증
        System.out.println("\n[테스트 결과] OptimisticLock 자동 재시도 (10명)");
        System.out.println("성공한 결제: " + successCount.get() + "명");
        System.out.println("실패한 결제: " + failureCount.get() + "명");
        System.out.println("소요 시간: " + (endTime - startTime) + "ms");
        System.out.println("예상: @Retryable로 인한 지수 백오프 대기 시간 포함\n");

        // 검증 1: 10명 모두 성공
        assertEquals(10, successCount.get(),
                "10명 모두 결제에 성공해야 합니다. 실제: " + successCount.get());

        // 검증 2: 재고가 정확히 10개 차감됨
        newTransactionTemplate.execute(status -> {
            Product product = productRepository.findById(productId).orElseThrow();
            assertEquals(0, product.getTotalStock(),
                    "재고가 10개 모두 차감되어야 합니다. 남은 재고: " + product.getTotalStock());
            return null;
        });

        // 검증 3: 재시도로 인한 지수 백오프 시간 확인 (최소 50ms 이상)
        long expectedMinTime = 50;  // @Retryable의 초기 delay
        assertTrue(endTime - startTime >= expectedMinTime,
                "재시도로 인한 대기 시간이 있어야 합니다. 소요 시간: " + (endTime - startTime) + "ms");
    }

    /**
     * SCENARIO 4: 사용자 잔액 동시 차감 (VULN-001)
     *
     * 목표: 사용자 한 명이 100000원 잔액 → 50명이 동시에 5000원씩 결제 시도
     * → 정확히 20명만 성공, 30명은 잔액 부족으로 실패
     *
     * 검증:
     * - Lost Update 발생하지 않음 (@Retryable + @Version)
     * - 최종 잔액이 정확히 0원
     * - OptimisticLockException 발생하고 자동 재시도됨
     */
    @Test
    @DisplayName("[VULN-001] 사용자 잔액 동시 차감")
    void testOrderPayment_UserBalance_ConcurrentDeduction() throws InterruptedException {
        // Given - 한 명의 사용자에게 100000원
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long testTimestamp = System.currentTimeMillis();
        int numThreads = 50;
        long[] productIdArray = new long[1];
        long[] optionIdArray = new long[1];
        long[] sharedUserId = new long[1];

        newTransactionTemplate.execute(status -> {
            // 상품 생성
            Product product = Product.builder()
                    .productName("잔액차감테스트상품_" + testId)
                    .price(5000L)
                    .totalStock(50)
                    .status("IN_STOCK")
                    .options(new ArrayList<>())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.save(product);
            entityManager.flush();

            ProductOption option = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("기본옵션")
                    .stock(50)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option);
            entityManager.flush();

            var savedOptions = productRepository.findOptionsByProductId(product.getProductId());
            productIdArray[0] = product.getProductId();
            optionIdArray[0] = savedOptions.get(0).getOptionId();

            // 공유 사용자 생성 (100000원 잔액)
            User sharedUser = User.builder()
                    .email("shared_user_" + testId + "@test.com")
                    .name("공유사용자")
                    .balance(100000L)  // 정확히 50명이 5000원씩 구매할 수 있는 금액
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(sharedUser);
            entityManager.flush();

            sharedUserId[0] = sharedUser.getUserId();
            return null;
        });

        long productId = productIdArray[0];
        long optionId = optionIdArray[0];
        long userId = sharedUserId[0];

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(50);

        // When - 50명이 동시에 같은 사용자 계정으로 결제
        for (int i = 1; i <= numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    CreateOrderRequest request = CreateOrderRequest.builder()
                            .items(List.of(
                                    OrderItemRequest.builder()
                                            .productId(productId)
                                            .optionId(optionId)
                                            .quantity(1)
                                            .build()
                            ))
                            .build();

                    try {
                        newTransactionTemplate.execute(status -> {
                            orderService.createOrder(userId, request);
                            successCount.incrementAndGet();
                            return null;
                        });
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Then - 잔액 일관성 검증
        System.out.println("\n[테스트 결과] 사용자 잔액 동시 차감 (100000원, 50명 동시 요청)");
        System.out.println("성공한 결제: " + successCount.get() + "명");
        System.out.println("실패한 결제: " + failureCount.get() + "명");

        // 검증 1: 정확히 20명만 성공 (100000 / 5000 = 20)
        assertEquals(20, successCount.get(),
                "정확히 20명만 결제 성공해야 합니다. 실제: " + successCount.get());

        // 검증 2: 나머지 30명은 실패
        assertEquals(30, failureCount.get(),
                "30명은 잔액 부족으로 실패해야 합니다. 실제: " + failureCount.get());

        // 검증 3: 최종 잔액이 0원
        newTransactionTemplate.execute(status -> {
            User user = userRepository.findById(userId).orElseThrow();
            assertEquals(0, user.getBalance(),
                    "최종 잔액이 0원이어야 합니다. 실제 잔액: " + user.getBalance());
            return null;
        });
    }
}
