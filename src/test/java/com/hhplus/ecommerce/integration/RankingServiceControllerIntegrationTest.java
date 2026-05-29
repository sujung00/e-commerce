package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.ranking.RankingService;
import com.hhplus.ecommerce.domain.ranking.RankingItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RankingService + RankingController 통합 테스트
 *
 * 테스트 시나리오:
 * 1. 여러 상품 주문 발생 → Redis 점수 증가 확인
 * 2. TOP N 조회 → 예상 순위와 일치 확인
 * 3. 특정 상품 순위 조회 → 예상 순위와 일치 확인
 *
 * 테스트 환경:
 * - BaseIntegrationTest 상속 (Redis 자동 관리)
 * - TestRestTemplate을 통한 HTTP 테스트
 * - 실제 Redis Sorted Set 동작 검증
 */
@DisplayName("[End-to-End] RankingService + RankingController 통합 테스트")
public class RankingServiceControllerIntegrationTest extends BaseIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(RankingServiceControllerIntegrationTest.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String TODAY_DATE = LocalDate.now().format(DATE_FORMATTER);
    private static final String RANKING_KEY = "ranking:daily:" + TODAY_DATE;

    @Autowired
    private RankingService rankingService;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // 각 테스트마다 Redis 랭킹 데이터 초기화
        redisTemplate.delete(RANKING_KEY);
        log.info("========== 통합 테스트 시작 ==========");
    }

    // ========== 시나리오 1: 여러 상품 주문 발생 → Redis 점수 증가 확인 ==========

    @Test
    @DisplayName("시나리오 1-1: 단일 상품 주문 발생 후 점수 증가 확인")
    void testScenario1_SingleProductOrder() throws Exception {
        // Given: 테스트 데이터
        Long productId = 100L;

        // When: 상품 주문 발생 (점수 증가)
        rankingService.incrementProductScore(productId);

        // Then: Redis에 점수가 저장되었는지 확인
        Long score = rankingService.getProductScore(productId);
        assertEquals(1L, score, "첫 주문 후 점수는 1이어야 합니다");

        // 추가 확인: Redis에 직접 접근해서 검증
        Double redisScore = redisTemplate.opsForZSet().score(RANKING_KEY, String.valueOf(productId));
        assertNotNull(redisScore, "Redis에 점수가 저장되어야 합니다");
        assertEquals(1.0, redisScore, "Redis 점수는 1.0이어야 합니다");

        log.info("✅ 시나리오 1-1 통과: 단일 상품 주문 후 점수 = {}", score);
    }

    @Test
    @DisplayName("시나리오 1-2: 여러 상품 주문 발생 후 각각의 점수 확인")
    void testScenario1_MultipleProductsOrder() throws Exception {
        // Given: 테스트 데이터
        Long product1 = 100L;
        Long product2 = 200L;
        Long product3 = 300L;

        // When: 여러 상품 주문 발생
        rankingService.incrementProductScore(product1);  // product1: 1점
        rankingService.incrementProductScore(product1);  // product1: 2점
        rankingService.incrementProductScore(product2);  // product2: 1점
        rankingService.incrementProductScore(product3);  // product3: 1점
        rankingService.incrementProductScore(product1);  // product1: 3점

        // Then: 각 상품의 점수 확인
        Long score1 = rankingService.getProductScore(product1);
        Long score2 = rankingService.getProductScore(product2);
        Long score3 = rankingService.getProductScore(product3);

        assertEquals(3L, score1, "product1의 점수는 3이어야 합니다");
        assertEquals(1L, score2, "product2의 점수는 1이어야 합니다");
        assertEquals(1L, score3, "product3의 점수는 1이어야 합니다");

        log.info("✅ 시나리오 1-2 통과:");
        log.info("   - product1: {} 점", score1);
        log.info("   - product2: {} 점", score2);
        log.info("   - product3: {} 점", score3);
    }

    @Test
    @DisplayName("시나리오 1-3: 많은 양의 주문 발생 후 점수 누적 확인")
    void testScenario1_ManyOrdersAccumulation() throws Exception {
        // Given: 테스트 데이터
        Long productId = 100L;
        int orderCount = 100;

        // When: 많은 주문 발생
        for (int i = 0; i < orderCount; i++) {
            rankingService.incrementProductScore(productId);
        }

        // Then: 누적 점수 확인
        Long score = rankingService.getProductScore(productId);
        assertEquals((long) orderCount, score, "100개의 주문 후 점수는 100이어야 합니다");

        log.info("✅ 시나리오 1-3 통과: {} 개 주문 후 점수 = {}", orderCount, score);
    }

    // ========== 시나리오 2: TOP N 조회 → 예상 순위와 일치 확인 ==========

    @Test
    @DisplayName("시나리오 2-1: TOP 5 조회 - 점수 내림차순 정렬 확인")
    void testScenario2_Top5SortedByScore() throws Exception {
        // Given: 테스트 데이터 준비 (의도적으로 순서 섞음) - 5개 상품
        rankingService.incrementProductScore(300L);  // 1점
        for (int i = 0; i < 5; i++) rankingService.incrementProductScore(200L);  // 5점
        for (int i = 0; i < 3; i++) rankingService.incrementProductScore(300L);  // 총 4점
        for (int i = 0; i < 10; i++) rankingService.incrementProductScore(50L);  // 10점 (최고)
        for (int i = 0; i < 7; i++) rankingService.incrementProductScore(150L);  // 7점
        for (int i = 0; i < 2; i++) rankingService.incrementProductScore(400L);  // 2점 (5등)

        // When: TOP 5 조회
        List<RankingItem> topProducts = rankingService.getTopProducts(5);

        // Then: 예상 순서대로 정렬되었는지 확인
        assertEquals(5, topProducts.size(), "5개의 상품이 반환되어야 합니다");
        assertEquals(50L, topProducts.get(0).getProductId(), "1등: product 50 (10점)");
        assertEquals(10L, topProducts.get(0).getScore());
        assertEquals(150L, topProducts.get(1).getProductId(), "2등: product 150 (7점)");
        assertEquals(7L, topProducts.get(1).getScore());
        assertEquals(200L, topProducts.get(2).getProductId(), "3등: product 200 (5점)");
        assertEquals(5L, topProducts.get(2).getScore());

        log.info("✅ 시나리오 2-1 통과: TOP 5 정렬 확인");
        logRankingResults(topProducts);
    }

    @Test
    @DisplayName("시나리오 2-2: TOP N 조회 REST API - JSON 응답 검증")
    void testScenario2_TopNRestApi() throws Exception {
        // Given: 테스트 데이터 준비
        for (int i = 0; i < 5; i++) rankingService.incrementProductScore(100L);
        for (int i = 0; i < 3; i++) rankingService.incrementProductScore(200L);
        for (int i = 0; i < 2; i++) rankingService.incrementProductScore(300L);

        // When: REST API 호출
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/ranking/top/3",
                String.class
        );

        // Then: 응답 검증
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        // JSON 파싱
        JsonNode rootNode = objectMapper.readTree(response.getBody());
        JsonNode topProductsNode = rootNode.get("top_products");

        assertNotNull(topProductsNode, "top_products 필드가 있어야 합니다");
        assertTrue(topProductsNode.isArray(), "top_products는 배열이어야 합니다");
        assertEquals(3, topProductsNode.size(), "3개의 상품이 반환되어야 합니다");

        // 첫 번째 상품 확인 (최고 점수)
        JsonNode firstProduct = topProductsNode.get(0);
        assertEquals(100, firstProduct.get("product_id").asInt(), "1등 상품 ID는 100");
        assertEquals(5, firstProduct.get("score").asInt(), "1등 상품 점수는 5");

        log.info("✅ 시나리오 2-2 통과: TOP 3 REST API 응답");
        log.info("   응답 JSON:\n{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode));
    }

    @Test
    @DisplayName("시나리오 2-3: TOP N 요청값이 실제 상품 수보다 큰 경우")
    void testScenario2_TopNGreaterThanActualCount() throws Exception {
        // Given: 3개 상품만 주문 (하지만 TOP 10 요청)
        rankingService.incrementProductScore(100L);
        rankingService.incrementProductScore(200L);
        rankingService.incrementProductScore(300L);

        // When: TOP 10 조회
        List<RankingItem> topProducts = rankingService.getTopProducts(10);

        // Then: 실제 개수인 3개만 반환
        assertEquals(3, topProducts.size(), "요청한 10개 중 실제 3개만 반환되어야 합니다");

        log.info("✅ 시나리오 2-3 통과: 존재하는 3개 상품만 반환");
    }

    // ========== 시나리오 3: 특정 상품 순위 조회 → 예상 순위와 일치 확인 ==========

    @Test
    @DisplayName("시나리오 3-1: 특정 상품의 순위 조회 - 정확한 순위 확인")
    void testScenario3_SpecificProductRank() throws Exception {
        // Given: 테스트 데이터 준비 (순위 설정)
        for (int i = 0; i < 10; i++) rankingService.incrementProductScore(50L);   // 1등 (10점)
        for (int i = 0; i < 7; i++) rankingService.incrementProductScore(150L);   // 2등 (7점)
        for (int i = 0; i < 5; i++) rankingService.incrementProductScore(200L);   // 3등 (5점)
        for (int i = 0; i < 3; i++) rankingService.incrementProductScore(300L);   // 4등 (3점)
        rankingService.incrementProductScore(400L);                              // 5등 (1점)

        // When: 각 상품의 순위 조회
        Optional<Long> rank50 = rankingService.getProductRank(50L);
        Optional<Long> rank150 = rankingService.getProductRank(150L);
        Optional<Long> rank200 = rankingService.getProductRank(200L);
        Optional<Long> rank300 = rankingService.getProductRank(300L);
        Optional<Long> rank400 = rankingService.getProductRank(400L);

        // Then: 예상 순위와 일치 확인
        assertTrue(rank50.isPresent(), "product 50은 랭킹에 있어야 합니다");
        assertEquals(1L, rank50.get(), "product 50의 순위는 1등");

        assertTrue(rank150.isPresent());
        assertEquals(2L, rank150.get(), "product 150의 순위는 2등");

        assertTrue(rank200.isPresent());
        assertEquals(3L, rank200.get(), "product 200의 순위는 3등");

        assertTrue(rank300.isPresent());
        assertEquals(4L, rank300.get(), "product 300의 순위는 4등");

        assertTrue(rank400.isPresent());
        assertEquals(5L, rank400.get(), "product 400의 순위는 5등");

        log.info("✅ 시나리오 3-1 통과: 모든 상품의 순위가 예상대로 일치");
    }

    @Test
    @DisplayName("시나리오 3-2: 특정 상품 순위 조회 REST API - JSON 응답 검증")
    void testScenario3_ProductRankRestApi() throws Exception {
        // Given: 테스트 데이터 준비
        for (int i = 0; i < 5; i++) rankingService.incrementProductScore(100L);
        rankingService.incrementProductScore(200L);

        // When: 상품 100의 순위 조회 (1등)
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/ranking/100",
                String.class
        );

        // Then: 응답 검증
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        // JSON 파싱
        JsonNode productNode = objectMapper.readTree(response.getBody());
        assertEquals(100, productNode.get("product_id").asInt(), "상품 ID는 100");
        assertEquals(1, productNode.get("rank").asInt(), "순위는 1등");
        assertEquals(5, productNode.get("score").asInt(), "점수는 5");

        // message 필드는 null (성공한 경우) → @JsonInclude(NON_NULL)로 필드 자체가 제거됨
        assertNull(productNode.get("message"), "message 필드는 JSON에 없어야 합니다 (NON_NULL)");

        log.info("✅ 시나리오 3-2 통과: 상품 순위 REST API 응답");
        log.info("   응답 JSON:\n{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(productNode));
    }

    @Test
    @DisplayName("시나리오 3-3: 랭킹에 없는 상품 조회")
    void testScenario3_ProductNotInRanking() throws Exception {
        // Given: 상품 100만 주문
        rankingService.incrementProductScore(100L);

        // When: 상품 999 (존재하지 않는 상품) 순위 조회
        Optional<Long> rank = rankingService.getProductRank(999L);

        // Then: 순위 없음 확인
        assertTrue(rank.isEmpty(), "상품 999는 랭킹에 없어야 합니다");

        // REST API로도 확인
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/ranking/999",
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode productNode = objectMapper.readTree(response.getBody());
        assertEquals(999, productNode.get("product_id").asInt());
        assertNull(productNode.get("rank"), "rank 필드는 JSON에 없어야 합니다 (NON_NULL)");
        assertEquals(0, productNode.get("score").asInt(), "점수는 0");
        assertTrue(productNode.get("message").asText().contains("랭킹에 없습니다"));

        log.info("✅ 시나리오 3-3 통과: 랭킹에 없는 상품 조회");
        log.info("   응답 JSON:\n{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(productNode));
    }

    // ========== 종합 시나리오 ==========

    @Test
    @DisplayName("종합 시나리오: 복잡한 주문 시나리오 후 전체 동작 검증")
    void testComprehensiveScenario() throws Exception {
        log.info("\n========== 종합 시나리오 시작 ==========");

        // ========== Phase 1: 주문 발생 ==========
        log.info("\n📍 Phase 1: 여러 상품의 주문 발생");

        // 상품별 주문 데이터
        long[][] orderData = {
                {100L, 15},  // product100: 15주
                {200L, 12},  // product200: 12주
                {300L, 8},   // product300: 8주
                {400L, 5},   // product400: 5주
                {500L, 3}    // product500: 3주
        };

        // 주문 생성
        for (long[] data : orderData) {
            long productId = data[0];
            long orderCount = data[1];
            for (long i = 0; i < orderCount; i++) {
                rankingService.incrementProductScore(productId);
            }
            log.info("   • Product {}: {} 주문 완료", productId, orderCount);
        }

        // ========== Phase 2: TOP 5 조회 ==========
        log.info("\n📍 Phase 2: TOP 5 상품 조회 및 REST API 검증");
        List<RankingItem> topProducts = rankingService.getTopProducts(5);

        assertEquals(5, topProducts.size(), "5개 상품이 반환되어야 합니다");

        // 순서 확인: 내림차순 정렬
        for (int i = 0; i < topProducts.size() - 1; i++) {
            assertTrue(
                    topProducts.get(i).getScore() >= topProducts.get(i + 1).getScore(),
                    "점수가 내림차순으로 정렬되어야 합니다"
            );
        }

        log.info("   • TOP 5 상품 정렬 확인 완료");
        logRankingResults(topProducts);

        // REST API로도 검증
        ResponseEntity<String> restResponse = restTemplate.getForEntity(
                "/api/ranking/top/5",
                String.class
        );
        assertEquals(HttpStatus.OK, restResponse.getStatusCode());

        JsonNode restTopProducts = objectMapper.readTree(restResponse.getBody()).get("top_products");
        assertEquals(5, restTopProducts.size(), "REST API도 5개 상품 반환");
        log.info("   • REST API 검증 완료");

        // ========== Phase 3: 개별 상품 순위 조회 ==========
        log.info("\n📍 Phase 3: 개별 상품 순위 조회");

        for (int i = 0; i < topProducts.size(); i++) {
            long productId = topProducts.get(i).getProductId();
            Optional<Long> rank = rankingService.getProductRank(productId);

            assertTrue(rank.isPresent(), "상품 " + productId + "는 랭킹에 있어야 합니다");
            assertEquals((long) (i + 1), rank.get(), "상품 " + productId + "의 순위는 " + (i + 1) + "등");

            // REST API로도 확인
            ResponseEntity<String> rankResponse = restTemplate.getForEntity(
                    "/api/ranking/" + productId,
                    String.class
            );
            JsonNode rankNode = objectMapper.readTree(rankResponse.getBody());
            assertEquals(i + 1, rankNode.get("rank").asInt(),
                    "REST API의 상품 " + productId + " 순위는 " + (i + 1) + "등");

            log.info("   • Product {}: {}등 (점수: {})", productId, rank.get(), topProducts.get(i).getScore());
        }

        // ========== Phase 4: 종합 결과 출력 ==========
        log.info("\n========== 종합 시나리오 통과 ✅ ==========");
        log.info("\n📊 최종 랭킹 결과:");
        logRankingResults(topProducts);
    }

    // ========== 헬퍼 메서드 ==========

    private void logRankingResults(List<RankingItem> rankings) {
        log.info("\n┌─────────────────────────────────────────────┐");
        log.info("│           🏆 상품 랭킹 결과                 │");
        log.info("├─────────────────────────────────────────────┤");

        for (int i = 0; i < rankings.size(); i++) {
            RankingItem item = rankings.get(i);
            String medal = switch (i) {
                case 0 -> "🥇";
                case 1 -> "🥈";
                case 2 -> "🥉";
                default -> "  ";
            };
            log.info("│ {}  {}등: Product {} - {}점         │",
                    medal, (i + 1), item.getProductId(), item.getScore());
        }

        log.info("└─────────────────────────────────────────────┘");
    }
}
