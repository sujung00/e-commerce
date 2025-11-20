package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.cart.CartService;
import com.hhplus.ecommerce.domain.cart.Cart;
import com.hhplus.ecommerce.domain.cart.CartItem;
import com.hhplus.ecommerce.domain.cart.CartRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.presentation.cart.request.AddCartItemRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IntegrationCartConcurrencyTest - 장바구니 동시성 제어 테스트
 *
 * 테스트 목표:
 * 1. 카트 항목 중복 처리 (VULN-007)
 * 2. 카트 총액 계산 Race Condition (VULN-002)
 * 3. 동시에 같은 상품을 여러 번 추가할 때 수량 누적
 * 4. UNIQUE 제약 위반 방지
 * 5. 카트 총액 일관성 보장
 *
 * 특징:
 * - TestContainers MySQL 사용
 * - 멀티스레드 환경에서 카트 아이템 중복 추가 재현
 * - CountDownLatch로 정확한 동시 실행 보장
 * - 수량 누적과 UNIQUE 제약 검증
 */
@SpringBootTest
@DisplayName("장바구니 동시성 제어 테스트")
class IntegrationCartConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private UserRepository userRepository;

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
     * SCENARIO 1: 카트 항목 중복 추가 (VULN-007)
     *
     * 목표: 같은 상품+옵션을 100명이 동시에 추가 → 수량이 100으로 누적되어야 함
     *
     * 문제 (VULN-007):
     * T1: findCartItem(cart=1, product=10, option=101) → 없음
     * T2: findCartItem(cart=1, product=10, option=101) → 없음
     * T1: INSERT cart_items(1, 10, 101, qty=1) ✓
     * T2: INSERT cart_items(1, 10, 101, qty=1) ❌ UNIQUE 제약 위반!
     *
     * 해결책: findCartItem()으로 중복 확인 후 수량 누적
     * T1: 새 항목 생성 ✓
     * T2: 기존 항목 수량 +1 → qty=2 ✓
     */
    @Test
    @DisplayName("[VULN-007] 카트 항목 중복 추가 시 수량 누적")
    void testCartItem_DuplicateAdd_QuantityAccumulation() throws InterruptedException {
        // Given - 사용자와 초기 카트 생성
        String testId = UUID.randomUUID().toString().substring(0, 8);
        int numThreads = 100;
        long[] userIdArray = new long[1];

        newTransactionTemplate.execute(status -> {
            User user = User.builder()
                    .email("cart_user_" + testId + "@test.com")
                    .name("카트테스트사용자")
                    .balance(1000000L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
            entityManager.flush();
            userIdArray[0] = user.getUserId();
            return null;
        });

        long userId = userIdArray[0];

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(100);

        // When - 100명이 동시에 같은 상품을 장바구니에 추가
        for (int i = 1; i <= numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    AddCartItemRequest request = AddCartItemRequest.builder()
                            .productId(1L)
                            .optionId(101L)
                            .quantity(1)
                            .build();

                    try {
                        newTransactionTemplate.execute(status -> {
                            cartService.addItem(userId, request);
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

        // Then - 중복 처리 검증
        System.out.println("\n[테스트 결과] 카트 항목 중복 추가 (100명 동시 요청)");
        System.out.println("성공: " + successCount.get() + "명");
        System.out.println("실패: " + failureCount.get() + "명");

        // 검증 1: 모두 성공해야 함 (수량 누적)
        assertEquals(100, successCount.get(),
                "100명 모두 성공해야 합니다. 실제: " + successCount.get());

        // 검증 2: 카트에 항목이 1개만 있어야 함
        newTransactionTemplate.execute(status -> {
            Cart cart = cartRepository.findByUserId(userId).orElseThrow();
            List<CartItem> items = cartRepository.getCartItems(cart.getCartId());

            assertEquals(1, items.size(),
                    "카트에 항목이 1개만 있어야 합니다. 실제: " + items.size());

            // 검증 3: 수량이 100으로 누적되어야 함
            CartItem item = items.get(0);
            assertEquals(100, item.getQuantity(),
                    "수량이 100으로 누적되어야 합니다. 실제: " + item.getQuantity());

            // 검증 4: 카트 총액도 정확함
            long expectedSubtotal = 1L * 101L * 100;  // product=1, option=101, qty=100
            assertEquals(expectedSubtotal, item.getSubtotal(),
                    "소계가 정확해야 합니다. 실제: " + item.getSubtotal());

            return null;
        });
    }

    /**
     * SCENARIO 2: 카트 총액 계산 Race Condition (VULN-002)
     *
     * 목표: 카트 총액이 정확히 계산되어야 함 (동시 추가/제거 시)
     *
     * 문제 (VULN-002):
     * T1: READ carts.total_price = 0
     * T2: READ carts.total_price = 0
     * T1: ADD item, total_price = 10000
     * T2: ADD item, total_price = 10000 (덮어쓰기!)
     * 최종: 20000이어야 하는데 10000이 됨
     *
     * 해결책: @Transactional + getCartItemsWithLock()
     */
    @Test
    @DisplayName("[VULN-002] 카트 총액 계산 일관성")
    void testCart_TotalPrice_RaceCondition() throws InterruptedException {
        // Given
        String testId = UUID.randomUUID().toString().substring(0, 8);
        int numThreads = 50;
        long[] userIdArray = new long[1];

        newTransactionTemplate.execute(status -> {
            User user = User.builder()
                    .email("cart_total_user_" + testId + "@test.com")
                    .name("카트총액테스트사용자")
                    .balance(1000000L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
            entityManager.flush();
            userIdArray[0] = user.getUserId();
            return null;
        });

        long userId = userIdArray[0];

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(50);

        // When - 50명이 동시에 다른 상품을 추가 (총 50개 항목)
        for (int i = 1; i <= numThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    long productId = index;
                    long optionId = 100L + index;

                    AddCartItemRequest request = AddCartItemRequest.builder()
                            .productId(productId)
                            .optionId(optionId)
                            .quantity(1)
                            .build();

                    try {
                        newTransactionTemplate.execute(status -> {
                            cartService.addItem(userId, request);
                            successCount.incrementAndGet();
                            return null;
                        });
                    } catch (Exception e) {
                        // 무시
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

        // Then - 총액 일관성 검증
        System.out.println("\n[테스트 결과] 카트 총액 일관성 (50명 동시 추가)");
        System.out.println("성공: " + successCount.get() + "명");

        newTransactionTemplate.execute(status -> {
            Cart cart = cartRepository.findByUserId(userId).orElseThrow();
            List<CartItem> items = cartRepository.getCartItems(cart.getCartId());

            System.out.println("카트 항목 수: " + items.size());
            System.out.println("카트 총액: " + cart.getTotalPrice());
            System.out.println("항목별 소계 합계: " + items.stream().mapToLong(CartItem::getSubtotal).sum());

            // 검증 1: 50개 항목 모두 추가됨
            assertEquals(50, items.size(),
                    "50개 항목이 추가되어야 합니다. 실제: " + items.size());

            // 검증 2: 카트 총액이 일치해야 함
            long expectedTotal = items.stream().mapToLong(CartItem::getSubtotal).sum();
            assertEquals(expectedTotal, cart.getTotalPrice(),
                    "카트 총액이 항목의 합계와 일치해야 합니다. 예상: " + expectedTotal + ", 실제: " + cart.getTotalPrice());

            return null;
        });
    }

    /**
     * SCENARIO 3: 동시 추가 후 중복 항목 병합 검증
     *
     * 목표: 10개 스레드 × 5회 추가 = 50회 추가 → 1개 항목에 qty=50으로 병합
     */
    @Test
    @DisplayName("[카트 중복 항목 병합] 동시 추가 후 수량 누적")
    void testCart_DuplicateItemMerge_MultipleAdds() throws InterruptedException {
        // Given
        String testId = UUID.randomUUID().toString().substring(0, 8);
        int numThreads = 10;
        int addsPerThread = 5;
        long[] userIdArray = new long[1];

        newTransactionTemplate.execute(status -> {
            User user = User.builder()
                    .email("cart_merge_user_" + testId + "@test.com")
                    .name("카트병합테스트사용자")
                    .balance(1000000L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
            entityManager.flush();
            userIdArray[0] = user.getUserId();
            return null;
        });

        long userId = userIdArray[0];

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads * addsPerThread);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(10);

        // When - 10개 스레드 × 5회 추가 (총 50회)
        for (int i = 1; i <= numThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < addsPerThread; j++) {
                        try {
                            startLatch.await();

                            AddCartItemRequest request = AddCartItemRequest.builder()
                                    .productId(1L)
                                    .optionId(101L)
                                    .quantity(1)
                                    .build();

                            try {
                                newTransactionTemplate.execute(status -> {
                                    cartService.addItem(userId, request);
                                    successCount.incrementAndGet();
                                    return null;
                                });
                            } catch (Exception e) {
                                // 무시
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            endLatch.countDown();
                        }
                    }
                } catch (Exception e) {
                    // 무시
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Then - 병합 검증
        System.out.println("\n[테스트 결과] 카트 항목 병합 (10스레드 × 5회 = 50회)");
        System.out.println("성공한 추가: " + successCount.get() + "회");

        newTransactionTemplate.execute(status -> {
            Cart cart = cartRepository.findByUserId(userId).orElseThrow();
            List<CartItem> items = cartRepository.getCartItems(cart.getCartId());

            System.out.println("최종 카트 항목 수: " + items.size());
            if (!items.isEmpty()) {
                System.out.println("항목 수량: " + items.get(0).getQuantity());
            }

            // 검증 1: 항목이 1개로 병합됨
            assertEquals(1, items.size(),
                    "항목이 1개로 병합되어야 합니다. 실제: " + items.size());

            // 검증 2: 수량이 50으로 누적됨
            assertEquals(50, items.get(0).getQuantity(),
                    "수량이 50으로 누적되어야 합니다. 실제: " + items.get(0).getQuantity());

            return null;
        });
    }
}
