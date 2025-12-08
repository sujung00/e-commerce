package com.hhplus.ecommerce.presentation.ranking;

import com.hhplus.ecommerce.application.ranking.RankingService;
import com.hhplus.ecommerce.domain.ranking.RankingItem;
import com.hhplus.ecommerce.presentation.ranking.response.TopProductsResponse;
import com.hhplus.ecommerce.presentation.ranking.response.ProductRankResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * RankingController - 상품 랭킹 REST API
 *
 * 역할:
 * - 실시간 상품 주문 랭킹 조회 API 제공
 * - TOP N 상품 조회
 * - 특정 상품 순위 확인
 *
 * 엔드포인트:
 * - GET /ranking/top/{topN} → TOP N 상품 조회
 * - GET /ranking/{productId} → 특정 상품 순위 조회
 *
 * 응답:
 * - JSON 형식
 * - HTTP 상태 코드 포함 (200, 400, 404, 500)
 *
 * 예시:
 * GET /ranking/top/5
 * → [{"productId": 100, "score": 150}, {"productId": 200, "score": 120}, ...]
 *
 * GET /ranking/100
 * → {"productId": 100, "rank": 1, "score": 150}
 */
@RestController
@RequestMapping("/ranking")
public class RankingController {

    private static final Logger log = LoggerFactory.getLogger(RankingController.class);

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    /**
     * TOP N 상품 조회
     *
     * HTTP Request:
     * GET /ranking/top/5
     *
     * 동작:
     * - 오늘 기준 상위 5개 상품 조회
     * - 점수(주문 수) 기준 내림차순 정렬
     * - 상품이 없으면 빈 리스트 반환
     *
     * Response:
     * 200 OK: {
     *   "topProducts": [
     *     {"productId": 100, "score": 150},
     *     {"productId": 200, "score": 120},
     *     ...
     *   ]
     * }
     *
     * 400 Bad Request:
     * - topN <= 0 인 경우
     *
     * 500 Internal Server Error:
     * - Redis 연결 실패 등 예외 발생
     *
     * @param topN 상위 몇 개 (1 이상)
     * @return TOP N 상품 리스트
     */
    @GetMapping("/top/{topN}")
    public ResponseEntity<TopProductsResponse> getTopProducts(
            @PathVariable(name = "topN") long topN) {

        log.info("[RankingController] TOP 상품 조회 요청: topN={}", topN);

        // 입력 검증
        if (topN <= 0) {
            log.warn("[RankingController] 유효하지 않은 topN 값: {}", topN);
            return ResponseEntity.badRequest()
                    .body(TopProductsResponse.of(List.of(), "topN은 1 이상이어야 합니다"));
        }

        try {
            // TOP N 상품 조회
            List<RankingItem> topProducts = rankingService.getTopProducts(topN);

            log.info("[RankingController] TOP 상품 조회 완료: topN={}, count={}", topN, topProducts.size());

            return ResponseEntity.ok(TopProductsResponse.of(topProducts));
        } catch (Exception e) {
            log.error("[RankingController] TOP 상품 조회 실패: topN={}", topN, e);
            return ResponseEntity.internalServerError()
                    .body(TopProductsResponse.of(List.of(), "서버 오류: " + e.getMessage()));
        }
    }

    /**
     * 특정 상품 순위 조회
     *
     * HTTP Request:
     * GET /ranking/100
     *
     * 동작:
     * - 오늘 기준 특정 상품의 순위 조회
     * - 순위는 1부터 시작 (1등, 2등, ...)
     * - 랭킹에 없으면 null/empty 반환 (주문이 없는 상품)
     *
     * Response:
     * 200 OK: {
     *   "productId": 100,
     *   "rank": 5,
     *   "score": 150
     * }
     *
     * 200 OK (랭킹 없음): {
     *   "productId": 100,
     *   "rank": null,
     *   "score": 0,
     *   "message": "상품이 랭킹에 없습니다"
     * }
     *
     * 400 Bad Request:
     * - productId <= 0 인 경우
     *
     * 500 Internal Server Error:
     * - Redis 연결 실패 등 예외 발생
     *
     * @param productId 상품 ID
     * @return 상품 순위 정보
     */
    @GetMapping("/{productId}")
    public ResponseEntity<ProductRankResponse> getProductRank(
            @PathVariable(name = "productId") Long productId) {

        log.info("[RankingController] 상품 순위 조회 요청: productId={}", productId);

        // 입력 검증
        if (productId == null || productId <= 0) {
            log.warn("[RankingController] 유효하지 않은 상품 ID: {}", productId);
            return ResponseEntity.badRequest()
                    .body(ProductRankResponse.of(productId, null, 0L, "상품 ID는 1 이상이어야 합니다"));
        }

        try {
            // 상품 순위 조회
            Optional<Long> rank = rankingService.getProductRank(productId);

            if (rank.isPresent()) {
                // 랭킹에 있는 경우: 순위와 점수 함께 반환
                Long score = rankingService.getProductScore(productId);
                log.info("[RankingController] 상품 순위 조회 완료: productId={}, rank={}, score={}",
                         productId, rank.get(), score);
                return ResponseEntity.ok(ProductRankResponse.of(productId, rank.get(), score));
            } else {
                // 랭킹에 없는 경우 (주문이 없는 상품)
                log.info("[RankingController] 상품이 랭킹에 없음: productId={}", productId);
                return ResponseEntity.ok(
                        ProductRankResponse.of(productId, null, 0L, "상품이 랭킹에 없습니다")
                );
            }
        } catch (Exception e) {
            log.error("[RankingController] 상품 순위 조회 실패: productId={}", productId, e);
            return ResponseEntity.internalServerError()
                    .body(ProductRankResponse.of(productId, null, 0L, "서버 오류: " + e.getMessage()));
        }
    }
}
