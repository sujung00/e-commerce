package com.hhplus.ecommerce.unit.application.ranking;

import com.hhplus.ecommerce.application.ranking.RankingServiceImpl;
import com.hhplus.ecommerce.domain.ranking.RankingRepository;
import com.hhplus.ecommerce.domain.ranking.RankingItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RankingService + RankingController 시나리오 테스트
 *
 * Mock을 사용한 단위 테스트 (Docker 불필요)
 * 세 가지 주요 시나리오를 검증합니다:
 * 1. 여러 상품 주문 발생 → Redis 점수 증가 확인
 * 2. TOP N 조회 → 예상 순위와 일치 확인
 * 3. 특정 상품 순위 조회 → 예상 순위와 일치 확인
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("[Scenario] RankingService 통합 시나리오 테스트")
public class RankingServiceControllerScenarioTest {

    private static final Logger log = LoggerFactory.getLogger(RankingServiceControllerScenarioTest.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String TODAY_DATE = LocalDate.now().format(DATE_FORMATTER);

    private RankingServiceImpl rankingService;

    @Mock
    private RankingRepository rankingRepository;

    @BeforeEach
    void setUp() {
        rankingService = new RankingServiceImpl(rankingRepository);
        log.info("========== 시나리오 테스트 시작 ==========\n");
    }

    // ========== 시나리오 1: 여러 상품 주문 발생 → Redis 점수 증가 확인 ==========

    @Test
    @DisplayName("시나리오 1-1: 단일 상품 주문 후 점수 증가 확인")
    void scenario1_1_SingleProductOrder() {
        // Given: 상품 100의 주문 1건
        Long productId = 100L;

        // When: 점수 증가
        rankingService.incrementProductScore(productId);

        // Then: Repository의 incrementProductScore가 호출되었는지 확인
        verify(rankingRepository, times(1)).incrementProductScore(TODAY_DATE, productId);

        log.info("✅ 시나리오 1-1 통과: 상품 100의 단일 주문 처리됨");
    }

    @Test
    @DisplayName("시나리오 1-2: 여러 상품의 주문 발생")
    void scenario1_2_MultipleProductsOrder() {
        // Given: 여러 상품의 주문 데이터
        Long product1 = 100L;
        Long product2 = 200L;
        Long product3 = 300L;

        log.info("📍 시나리오 1-2: 여러 상품 주문 시뮬레이션");
        log.info("   • Product 100: 3주 (1주, 1주, 1주)");
        log.info("   • Product 200: 1주");
        log.info("   • Product 300: 1주\n");

        // When: 주문 발생
        rankingService.incrementProductScore(product1);
        rankingService.incrementProductScore(product1);
        rankingService.incrementProductScore(product2);
        rankingService.incrementProductScore(product3);
        rankingService.incrementProductScore(product1);

        // Then: 각 상품별로 올바른 횟수로 호출되었는지 확인
        verify(rankingRepository, times(3)).incrementProductScore(TODAY_DATE, product1);
        verify(rankingRepository, times(1)).incrementProductScore(TODAY_DATE, product2);
        verify(rankingRepository, times(1)).incrementProductScore(TODAY_DATE, product3);

        log.info("✅ 시나리오 1-2 통과: 모든 주문이 올바르게 처리됨");
    }

    @Test
    @DisplayName("시나리오 1-3: 대량 주문 발생")
    void scenario1_3_ManyOrdersAccumulation() {
        // Given: 대량 주문 데이터
        Long productId = 100L;
        int totalOrders = 100;

        log.info("📍 시나리오 1-3: 대량 주문 처리 (100건)");

        // When: 100개의 주문 처리
        for (int i = 0; i < totalOrders; i++) {
            rankingService.incrementProductScore(productId);
        }

        // Then: 100번 호출되었는지 확인
        verify(rankingRepository, times(totalOrders)).incrementProductScore(TODAY_DATE, productId);

        log.info("✅ 시나리오 1-3 통과: {} 개 주문 모두 처리됨\n", totalOrders);
    }

    // ========== 시나리오 2: TOP N 조회 → 예상 순위와 일치 확인 ==========

    @Test
    @DisplayName("시나리오 2-1: TOP 5 조회 - 점수 순서대로 정렬")
    void scenario2_1_Top5SortedByScore() {
        // Given: 상위 5개 상품의 랭킹 데이터
        List<RankingItem> topProducts = Arrays.asList(
                RankingItem.builder().productId(50L).score(10L).build(),   // 1등: 10점
                RankingItem.builder().productId(150L).score(7L).build(),   // 2등: 7점
                RankingItem.builder().productId(200L).score(5L).build(),   // 3등: 5점
                RankingItem.builder().productId(300L).score(4L).build(),   // 4등: 4점
                RankingItem.builder().productId(400L).score(1L).build()    // 5등: 1점
        );

        when(rankingRepository.getTopProducts(TODAY_DATE, 5)).thenReturn(topProducts);

        log.info("📍 시나리오 2-1: TOP 5 조회");

        // When: TOP 5 조회
        List<RankingItem> result = rankingService.getTopProducts(5);

        // Then: 정렬 순서 확인
        assertEquals(5, result.size(), "5개의 상품이 반환되어야 합니다");

        // 예상 순서대로 정렬되었는지 확인
        assertEquals(50L, result.get(0).getProductId(), "1등: Product 50");
        assertEquals(10L, result.get(0).getScore());

        assertEquals(150L, result.get(1).getProductId(), "2등: Product 150");
        assertEquals(7L, result.get(1).getScore());

        assertEquals(200L, result.get(2).getProductId(), "3등: Product 200");
        assertEquals(5L, result.get(2).getScore());

        assertEquals(300L, result.get(3).getProductId(), "4등: Product 300");
        assertEquals(4L, result.get(3).getScore());

        assertEquals(400L, result.get(4).getProductId(), "5등: Product 400");
        assertEquals(1L, result.get(4).getScore());

        log.info("   • 1등: Product 50 (10점) ✓");
        log.info("   • 2등: Product 150 (7점) ✓");
        log.info("   • 3등: Product 200 (5점) ✓");
        log.info("   • 4등: Product 300 (4점) ✓");
        log.info("   • 5등: Product 400 (1점) ✓");
        log.info("✅ 시나리오 2-1 통과: TOP 5 정렬 완벽\n");
    }

    @Test
    @DisplayName("시나리오 2-2: TOP N 요청 > 실제 상품 수")
    void scenario2_2_TopNGreaterThanActualCount() {
        // Given: 3개 상품만 있는 랭킹 (TOP 10 요청)
        List<RankingItem> topProducts = Arrays.asList(
                RankingItem.builder().productId(100L).score(5L).build(),
                RankingItem.builder().productId(200L).score(3L).build(),
                RankingItem.builder().productId(300L).score(1L).build()
        );

        when(rankingRepository.getTopProducts(TODAY_DATE, 10)).thenReturn(topProducts);

        log.info("📍 시나리오 2-2: TOP 10 요청 (실제 3개 상품만 존재)");

        // When: TOP 10 조회
        List<RankingItem> result = rankingService.getTopProducts(10);

        // Then: 실제 3개만 반환
        assertEquals(3, result.size(), "요청 10개 중 실제 3개만 반환");
        assertEquals(100L, result.get(0).getProductId());
        assertEquals(200L, result.get(1).getProductId());
        assertEquals(300L, result.get(2).getProductId());

        log.info("   • 요청: TOP 10");
        log.info("   • 반환: 3개 상품 (실제 존재하는 수만큼)");
        log.info("✅ 시나리오 2-2 통과: 올바른 개수 반환\n");
    }

    @Test
    @DisplayName("시나리오 2-3: TOP 1만 조회")
    void scenario2_3_Top1Only() {
        // Given: 최고 점수 상품 1개
        List<RankingItem> topProduct = Arrays.asList(
                RankingItem.builder().productId(100L).score(15L).build()
        );

        when(rankingRepository.getTopProducts(TODAY_DATE, 1)).thenReturn(topProduct);

        log.info("📍 시나리오 2-3: TOP 1 조회 (최고 점수 상품만)");

        // When: TOP 1 조회
        List<RankingItem> result = rankingService.getTopProducts(1);

        // Then: 1개만 반환
        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).getProductId());
        assertEquals(15L, result.get(0).getScore());

        log.info("   • 1등: Product 100 (15점)");
        log.info("✅ 시나리오 2-3 통과: 1개 상품 정확히 반환\n");
    }

    // ========== 시나리오 3: 특정 상품 순위 조회 → 예상 순위와 일치 확인 ==========

    @Test
    @DisplayName("시나리오 3-1: 특정 상품의 순위 조회")
    void scenario3_1_SpecificProductRank() {
        // Given: 5개 상품의 순위 데이터
        when(rankingRepository.getProductRank(TODAY_DATE, 50L)).thenReturn(Optional.of(1L));
        when(rankingRepository.getProductRank(TODAY_DATE, 150L)).thenReturn(Optional.of(2L));
        when(rankingRepository.getProductRank(TODAY_DATE, 200L)).thenReturn(Optional.of(3L));
        when(rankingRepository.getProductRank(TODAY_DATE, 300L)).thenReturn(Optional.of(4L));
        when(rankingRepository.getProductRank(TODAY_DATE, 400L)).thenReturn(Optional.of(5L));

        log.info("📍 시나리오 3-1: 개별 상품 순위 조회");

        // When & Then: 각 상품의 순위 조회 및 검증
        Optional<Long> rank50 = rankingService.getProductRank(50L);
        assertEquals(Optional.of(1L), rank50, "Product 50은 1등");

        Optional<Long> rank150 = rankingService.getProductRank(150L);
        assertEquals(Optional.of(2L), rank150, "Product 150은 2등");

        Optional<Long> rank200 = rankingService.getProductRank(200L);
        assertEquals(Optional.of(3L), rank200, "Product 200은 3등");

        Optional<Long> rank300 = rankingService.getProductRank(300L);
        assertEquals(Optional.of(4L), rank300, "Product 300은 4등");

        Optional<Long> rank400 = rankingService.getProductRank(400L);
        assertEquals(Optional.of(5L), rank400, "Product 400은 5등");

        log.info("   • Product 50: 1등 ✓");
        log.info("   • Product 150: 2등 ✓");
        log.info("   • Product 200: 3등 ✓");
        log.info("   • Product 300: 4등 ✓");
        log.info("   • Product 400: 5등 ✓");
        log.info("✅ 시나리오 3-1 통과: 모든 순위 일치\n");
    }

    @Test
    @DisplayName("시나리오 3-2: 랭킹에 없는 상품 조회")
    void scenario3_2_ProductNotInRanking() {
        // Given: 상품 999는 랭킹에 없음
        when(rankingRepository.getProductRank(TODAY_DATE, 999L)).thenReturn(Optional.empty());

        log.info("📍 시나리오 3-2: 랭킹에 없는 상품 조회");

        // When: 상품 999의 순위 조회
        Optional<Long> rank = rankingService.getProductRank(999L);

        // Then: 빈 결과 확인
        assertTrue(rank.isEmpty(), "상품 999는 랭킹에 없어야 합니다");

        log.info("   • Product 999: 랭킹 없음 (예상대로) ✓");
        log.info("✅ 시나리오 3-2 통과: 없는 상품 올바르게 처리\n");
    }

    @Test
    @DisplayName("시나리오 3-3: 특정 상품의 점수 조회")
    void scenario3_3_ProductScore() {
        // Given: 각 상품의 점수 데이터
        when(rankingRepository.getProductScore(TODAY_DATE, 100L)).thenReturn(15L);
        when(rankingRepository.getProductScore(TODAY_DATE, 200L)).thenReturn(12L);
        when(rankingRepository.getProductScore(TODAY_DATE, 300L)).thenReturn(8L);

        log.info("📍 시나리오 3-3: 특정 상품의 점수(주문 수) 조회");

        // When & Then: 각 상품의 점수 조회 및 검증
        Long score100 = rankingService.getProductScore(100L);
        assertEquals(15L, score100, "Product 100의 점수는 15");

        Long score200 = rankingService.getProductScore(200L);
        assertEquals(12L, score200, "Product 200의 점수는 12");

        Long score300 = rankingService.getProductScore(300L);
        assertEquals(8L, score300, "Product 300의 점수는 8");

        log.info("   • Product 100: 15주 ✓");
        log.info("   • Product 200: 12주 ✓");
        log.info("   • Product 300: 8주 ✓");
        log.info("✅ 시나리오 3-3 통과: 모든 점수 정확\n");
    }

    // ========== 종합 시나리오 ==========

    @Test
    @DisplayName("종합 시나리오: 실제 e-commerce 주문 플로우 시뮬레이션")
    void comprehensiveScenario() {
        log.info("\n╔════════════════════════════════════════════╗");
        log.info("║  🎯 종합 시나리오: 실제 주문 플로우 검증   ║");
        log.info("╚════════════════════════════════════════════╝\n");

        // ========== Phase 1: 주문 발생 ==========
        log.info("📍 Phase 1: 여러 상품의 주문 발생\n");

        // 주문 데이터
        long[][] orderData = {
                {100L, 15},  // 상품 100: 15주
                {200L, 12},  // 상품 200: 12주
                {300L, 8},   // 상품 300: 8주
                {400L, 5},   // 상품 400: 5주
                {500L, 3}    // 상품 500: 3주
        };

        for (long[] data : orderData) {
            long productId = data[0];
            long orderCount = data[1];
            for (long i = 0; i < orderCount; i++) {
                rankingService.incrementProductScore(productId);
            }
            log.info("   ✓ Product {}: {} 주문", productId, orderCount);
        }

        // 각 상품별로 올바른 횟수로 호출되었는지 검증
        verify(rankingRepository, times(15)).incrementProductScore(TODAY_DATE, 100L);
        verify(rankingRepository, times(12)).incrementProductScore(TODAY_DATE, 200L);
        verify(rankingRepository, times(8)).incrementProductScore(TODAY_DATE, 300L);
        verify(rankingRepository, times(5)).incrementProductScore(TODAY_DATE, 400L);
        verify(rankingRepository, times(3)).incrementProductScore(TODAY_DATE, 500L);

        // ========== Phase 2: TOP 5 조회 ==========
        log.info("\n📍 Phase 2: TOP 5 상품 조회\n");

        List<RankingItem> topProducts = Arrays.asList(
                RankingItem.builder().productId(100L).score(15L).build(),
                RankingItem.builder().productId(200L).score(12L).build(),
                RankingItem.builder().productId(300L).score(8L).build(),
                RankingItem.builder().productId(400L).score(5L).build(),
                RankingItem.builder().productId(500L).score(3L).build()
        );

        when(rankingRepository.getTopProducts(TODAY_DATE, 5)).thenReturn(topProducts);

        List<RankingItem> result = rankingService.getTopProducts(5);
        assertEquals(5, result.size());

        for (int i = 0; i < result.size(); i++) {
            String medal = switch (i) {
                case 0 -> "🥇";
                case 1 -> "🥈";
                case 2 -> "🥉";
                default -> "  ";
            };
            log.info("   {} {}등: Product {} ({} 주)", medal, (i + 1), result.get(i).getProductId(), result.get(i).getScore());
        }

        // ========== Phase 3: 개별 상품 순위 조회 ==========
        log.info("\n📍 Phase 3: 개별 상품 순위 조회\n");

        when(rankingRepository.getProductRank(TODAY_DATE, 100L)).thenReturn(Optional.of(1L));
        when(rankingRepository.getProductRank(TODAY_DATE, 200L)).thenReturn(Optional.of(2L));
        when(rankingRepository.getProductRank(TODAY_DATE, 300L)).thenReturn(Optional.of(3L));
        when(rankingRepository.getProductRank(TODAY_DATE, 400L)).thenReturn(Optional.of(4L));
        when(rankingRepository.getProductRank(TODAY_DATE, 500L)).thenReturn(Optional.of(5L));

        for (int i = 0; i < 5; i++) {
            Long productId = result.get(i).getProductId();
            Optional<Long> rank = rankingService.getProductRank(productId);
            assertTrue(rank.isPresent());
            assertEquals((long) (i + 1), rank.get());
            log.info("   ✓ Product {}: {}등", productId, rank.get());
        }

        // ========== Phase 4: 결과 검증 ==========
        log.info("\n╔════════════════════════════════════════════╗");
        log.info("║  ✅ 종합 시나리오 완벽하게 통과!          ║");
        log.info("╚════════════════════════════════════════════╝\n");

        log.info("📊 최종 결과:");
        log.info("   • 총 주문 건수: 43건");
        log.info("   • 상품 개수: 5개");
        log.info("   • TOP 5 정렬: ✓ 정확한 순서");
        log.info("   • 개별 순위: ✓ 모두 일치\n");
    }
}
