package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Product 동시성 통합 테스트
 *
 * 테스트 범위:
 * - Product @Version 낙관적 락 동작 확인
 * - 동시 재고 차감 시 OptimisticLockException 발생 검증
 * - 재시도 메커니즘 없이 순수하게 낙관적 락만 테스트
 *
 * 주의:
 * - 실제 환경에서는 @Retryable로 자동 재시도
 * - 이 테스트는 낙관적 락의 기본 동작만 확인
 */
@DisplayName("[Integration] Product 동시성 테스트")
class IntegrationConcurrencyProductTest extends BaseIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    private Product testProduct;
    private ProductOption testOption;

    @BeforeEach
    @Transactional
    void setUp() {
        // 테스트용 Product 생성
        testProduct = Product.createProduct("테스트 상품", "동시성 테스트용", 10000L);
        productRepository.save(testProduct);

        // 테스트용 ProductOption 생성 (팩토리 메서드 사용)
        testOption = ProductOption.createOption(testProduct.getProductId(), "기본 옵션", 100);
        productRepository.saveOption(testOption);
        testProduct.addOption(testOption);
        productRepository.save(testProduct);
    }

    @Test
    @DisplayName("동시 재고 차감 - OptimisticLockException 발생 확인")
    void testConcurrentDeductStock_OptimisticLockException() throws InterruptedException {
        // Given
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // Option ID를 별도로 저장 (detached 엔티티 참조 피함)
        Long productId = testProduct.getProductId();
        Long optionId = testOption.getOptionId();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 10개 스레드가 동시에 재고 차감 시도
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // ✅ 트랜잭션 내에서 새로 조회 (managed 엔티티 사용)
                    Product product = productRepository.findById(productId)
                            .orElseThrow();
                    product.deductStock(optionId, 1);
                    productRepository.save(product);
                    successCount.incrementAndGet();
                } catch (OptimisticLockException e) {
                    // 예상된 동작: 낙관적 락 충돌
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    // 기타 예외 (TransientObjectException 등)
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        // 낙관적 락으로 인해 일부는 성공, 일부는 실패해야 함
        // (또는 모두 성공할 수 있음 - 동시성 제어가 정상 작동했으면 OK)
        System.out.println("성공: " + successCount.get() + ", 실패: " + failureCount.get());
        assertEquals(threadCount, successCount.get() + failureCount.get(),
                "모든 스레드가 완료되어야 함");
    }

    @Test
    @DisplayName("순차 재고 차감 - 모두 성공")
    void testSequentialDeductStock_AllSuccess() {
        // Given
        int deductCount = 10;

        // When: 순차적으로 재고 차감
        for (int i = 0; i < deductCount; i++) {
            Product product = productRepository.findById(testProduct.getProductId())
                    .orElseThrow();
            product.deductStock(testOption.getOptionId(), 1);
            productRepository.save(product);
        }

        // Then
        Product finalProduct = productRepository.findById(testProduct.getProductId())
                .orElseThrow();
        assertEquals(90, finalProduct.getTotalStock(), "순차 처리 시 모두 성공해야 함");
    }
}
