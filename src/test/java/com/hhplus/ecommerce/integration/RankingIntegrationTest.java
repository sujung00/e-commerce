package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.ranking.RankingService;
import com.hhplus.ecommerce.domain.ranking.RankingItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RankingService 통합 테스트 (Redis 실제 사용)
 *
 * 테스트 범위:
 * - Redis Sorted Set 실제 동작
 * - 동시성 테스트 (여러 스레드의 동시 점수 증가)
 * - 성능 테스트
 * - 실제 데이터 정확성
 *
 * 환경:
 * - TestContainers: Docker 기반 Redis 자동 구성
 * - 격리된 테스트 환경: 각 테스트마다 독립적인 데이터
 *
 * 테스트 전략:
 * - Mock 없이 실제 Redis 동작 검증
 * - 동시성 환경에서의 데이터 정확성 확인
 */
@DisplayName("[Integration] RankingService Redis 통합 테스트")
public class RankingIntegrationTest extends BaseIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(RankingIntegrationTest.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String TODAY_DATE = LocalDate.now().format(DATE_FORMATTER);
    private static final String RANKING_KEY = "ranking:daily:" + TODAY_DATE;

    @Autowired
    private RankingService rankingService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // 각 테스트마다 Redis 랭킹 데이터 초기화
        redisTemplate.delete(RANKING_KEY);
        log.info("========== RankingService 통합 테스트 시작 ==========");
    }

    // ========== 기본 기능 테스트 ==========

    @Test
    @DisplayName("단일 상품 점수 증가")
    void testIncrementProductScore_SingleProduct() {
        // Given
        Long productId = 100L;

        // When
        rankingService.incrementProductScore(productId);

        // Then: 점수가 1이어야 함
        Long score = rankingService.getProductScore(productId);
        assertEquals(1L, score);
        log.info("✅ 단일 상품 점수 증가 테스트 통과: productId={}, score={}", productId, score);
    }

    @Test
    @DisplayName("동일 상품 점수 누적 증가")
    void testIncrementProductScore_Accumulation() {
        // Given
        Long productId = 100L;

        // When: 같은 상품이 5번 주문됨
        for (int i = 0; i < 5; i++) {
            rankingService.incrementProductScore(productId);
        }

        // Then: 점수가 5여야 함
        Long score = rankingService.getProductScore(productId);
        assertEquals(5L, score);
        log.info("✅ 점수 누적 증가 테스트 통과: productId={}, score={}", productId, score);
    }

    @Test
    @DisplayName("여러 상품의 점수 증가")
    void testIncrementProductScore_MultipleProducts() {
        // Given
        Long product1 = 100L;
        Long product2 = 200L;
        Long product3 = 300L;

        // When: 각 상품마다 다양한 주문 발생
        rankingService.incrementProductScore(product1);  // 1
        rankingService.incrementProductScore(product1);  // 2
        rankingService.incrementProductScore(product2);  // 1
        rankingService.incrementProductScore(product3);  // 1
        rankingService.incrementProductScore(product1);  // 3

        // Then: 각 상품의 점수 확인
        assertEquals(3L, rankingService.getProductScore(product1));
        assertEquals(1L, rankingService.getProductScore(product2));
        assertEquals(1L, rankingService.getProductScore(product3));
        log.info("✅ 여러 상품 점수 증가 테스트 통과");
    }

    // ========== TOP N 조회 테스트 ==========

    @Test
    @DisplayName("TOP 5 상품 조회 - 정렬 확인")
    void testGetTopProducts_SortedCorrectly() {
        // Given: 의도적으로 다양한 점수 설정
        rankingService.incrementProductScore(100L); // 1점
        for (int i = 0; i < 5; i++) rankingService.incrementProductScore(200L); // 5점
        for (int i = 0; i < 3; i++) rankingService.incrementProductScore(300L); // 3점
        for (int i = 0; i < 10; i++) rankingService.incrementProductScore(50L); // 10점
        for (int i = 0; i < 7; i++) rankingService.incrementProductScore(150L); // 7점

        // When
        List<RankingItem> topProducts = rankingService.getTopProducts(5);

        // Then: 내림차순(높은 점수부터) 정렬되어야 함
        assertEquals(5, topProducts.size());
        assertEquals(50L, topProducts.get(0).getProductId());   // 10점 (1등)
        assertEquals(10L, topProducts.get(0).getScore());
        assertEquals(150L, topProducts.get(1).getProductId());  // 7점 (2등)
        assertEquals(7L, topProducts.get(1).getScore());
        assertEquals(200L, topProducts.get(2).getProductId());  // 5점 (3등)
        assertEquals(5L, topProducts.get(2).getScore());
        log.info("✅ TOP 상품 정렬 테스트 통과: {}", topProducts);
    }

    @Test
    @DisplayName("TOP 1 상품 조회")
    void testGetTopProducts_Top1() {
        // Given
        rankingService.incrementProductScore(100L);
        for (int i = 0; i < 5; i++) rankingService.incrementProductScore(200L);

        // When
        List<RankingItem> topProducts = rankingService.getTopProducts(1);

        // Then
        assertEquals(1, topProducts.size());
        assertEquals(200L, topProducts.get(0).getProductId());
        assertEquals(5L, topProducts.get(0).getScore());
        log.info("✅ TOP 1 조회 테스트 통과");
    }

    @Test
    @DisplayName("TOP N > 실제 상품 수")
    void testGetTopProducts_TopNGreaterThanActual() {
        // Given: 3개 상품만 있음
        rankingService.incrementProductScore(100L);
        rankingService.incrementProductScore(200L);
        rankingService.incrementProductScore(300L);

        // When: TOP 10 조회
        List<RankingItem> topProducts = rankingService.getTopProducts(10);

        // Then: 3개만 반환
        assertEquals(3, topProducts.size());
        log.info("✅ TOP N > 실제 수량 테스트 통과: requested=10, actual={}", topProducts.size());
    }

    // ========== 순위 조회 테스트 ==========

    @Test
    @DisplayName("특정 상품의 순위 조회")
    void testGetProductRank_Success() {
        // Given: 점수에 따른 순위 설정
        rankingService.incrementProductScore(100L); // 1점
        for (int i = 0; i < 5; i++) rankingService.incrementProductScore(200L); // 5점
        for (int i = 0; i < 3; i++) rankingService.incrementProductScore(300L); // 3점

        // When & Then
        assertEquals(Optional.of(2L), rankingService.getProductRank(200L)); // 5점 (2등)
        assertEquals(Optional.of(3L), rankingService.getProductRank(300L)); // 3점 (3등)
        assertEquals(Optional.of(1L), rankingService.getProductRank(200L)); // 5점 (1등 재확인)
        log.info("✅ 순위 조회 테스트 통과");
    }

    @Test
    @DisplayName("랭킹에 없는 상품 순위 조회")
    void testGetProductRank_NotInRanking() {
        // Given
        rankingService.incrementProductScore(100L);

        // When & Then
        assertEquals(Optional.empty(), rankingService.getProductRank(999L));
        log.info("✅ 랭킹 없음 테스트 통과");
    }

    // ========== 동시성 테스트 ==========

    @Test
    @DisplayName("동시 점수 증가 - 데이터 정확성")
    void testConcurrentScoreIncrement() throws InterruptedException {
        // Given
        Long productId = 100L;
        int threadCount = 10;
        int incrementsPerThread = 100;

        // When: 10개 스레드가 각각 100번씩 점수 증가 (총 1000번)
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    rankingService.incrementProductScore(productId);
                }
            });
            threads[i].start();
        }

        // 모든 스레드 완료 대기
        for (Thread thread : threads) {
            thread.join();
        }

        // Then: 최종 점수는 정확히 1000이어야 함
        Long finalScore = rankingService.getProductScore(productId);
        assertEquals(1000L, finalScore);
        log.info("✅ 동시성 테스트 통과: 최종 점수 = {}", finalScore);
    }

    @Test
    @DisplayName("동시 다중 상품 점수 증가")
    void testConcurrentMultipleProducts() throws InterruptedException {
        // Given
        int threadCount = 5;
        int productsPerThread = 20;

        // When: 5개 스레드가 각각 20개 상품을 10번씩 증가
        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                for (long p = 1; p <= productsPerThread; p++) {
                    for (int i = 0; i < 10; i++) {
                        rankingService.incrementProductScore(p);
                    }
                }
            });
            threads[t].start();
        }

        // 모든 스레드 완료 대기
        for (Thread thread : threads) {
            thread.join();
        }

        // Then: 모든 상품이 50점(5 threads * 10)을 가져야 함
        for (long productId = 1; productId <= productsPerThread; productId++) {
            Long score = rankingService.getProductScore(productId);
            assertEquals(50L, score, "상품 " + productId + "의 점수가 올바르지 않음");
        }
        log.info("✅ 다중 상품 동시성 테스트 통과");
    }

    // ========== 성능 테스트 ==========

    @Test
    @DisplayName("성능 테스트 - 1000개 상품의 점수 증가")
    void testPerformance_1000Products() {
        // Given & When
        long startTime = System.currentTimeMillis();
        for (long i = 1; i <= 1000; i++) {
            rankingService.incrementProductScore(i);
        }
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: 빠른 성능 확인 (일반적으로 < 100ms)
        assertTrue(elapsedTime < 1000, "성능이 저하됨: " + elapsedTime + "ms");
        log.info("✅ 성능 테스트 통과: 1000개 상품 점수 증가 = {}ms", elapsedTime);
    }

    @Test
    @DisplayName("성능 테스트 - TOP 10 조회")
    void testPerformance_GetTopProducts() {
        // Given: 1000개 상품 준비
        for (long i = 1; i <= 1000; i++) {
            rankingService.incrementProductScore(i);
        }

        // When
        long startTime = System.currentTimeMillis();
        List<RankingItem> topProducts = rankingService.getTopProducts(10);
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: 빠른 조회 (일반적으로 < 10ms)
        assertTrue(elapsedTime < 100, "조회 성능이 저하됨: " + elapsedTime + "ms");
        assertEquals(10, topProducts.size());
        log.info("✅ 성능 테스트 통과: TOP 10 조회 = {}ms", elapsedTime);
    }

    @Test
    @DisplayName("성능 테스트 - 특정 상품 순위 조회")
    void testPerformance_GetProductRank() {
        // Given: 10000개 상품 준비
        for (long i = 1; i <= 10000; i++) {
            rankingService.incrementProductScore(i);
        }

        // When
        long startTime = System.currentTimeMillis();
        Optional<Long> rank = rankingService.getProductRank(5000L);
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: 빠른 조회 (O(log N) - 일반적으로 < 5ms)
        assertTrue(elapsedTime < 50, "순위 조회 성능이 저하됨: " + elapsedTime + "ms");
        assertTrue(rank.isPresent());
        log.info("✅ 성능 테스트 통과: 순위 조회 = {}ms, rank = {}", elapsedTime, rank);
    }
}
