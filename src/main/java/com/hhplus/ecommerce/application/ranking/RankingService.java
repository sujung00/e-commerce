package com.hhplus.ecommerce.application.ranking;

import com.hhplus.ecommerce.domain.ranking.RankingItem;

import java.util.List;
import java.util.Optional;

/**
 * RankingService - 상품 랭킹 비즈니스 로직
 *
 * 역할:
 * - 일일 상품 주문 랭킹 관리
 * - 실시간 TOP N 상품 조회
 * - 특정 상품 순위 확인
 *
 * 기술:
 * - Redis Sorted Set 활용
 * - Atomic 연산으로 동시성 보장
 * - O(log N) 성능
 *
 * 사용 시나리오:
 * 1. 주문 완료 시: incrementProductScore(productId) → 점수 1 증가
 * 2. 인기 상품 조회: getTopProducts(5) → TOP 5 상품
 * 3. 상품 순위 확인: getProductRank(productId) → 순위 조회
 */
public interface RankingService {

    /**
     * 상품 점수 증가 (주문 발생 시)
     *
     * 동작:
     * - 오늘 날짜 기준 ranking:daily:YYYYMMDD 에서 점수 1 증가
     * - 주문 완료 후 호출됨
     *
     * 예: 상품 ID 100번 주문 완료
     * → incrementProductScore(100L)
     * → Redis: ZADD ranking:daily:20241202 1 100 (점수 1 증가)
     *
     * Atomicity:
     * - Redis ZADD는 원자적 연산
     * - 여러 스레드의 동시 접근도 순서대로 처리 (데이터 경쟁 조건 없음)
     *
     * @param productId 상품 ID
     */
    void incrementProductScore(Long productId);

    /**
     * TOP N 상품 조회
     *
     * 동작:
     * - 오늘 날짜 기준으로 TOP N 상품 반환
     * - 점수가 높은 순서대로 정렬
     *
     * 예: 상위 5개 상품 조회
     * → getTopProducts(5)
     * → [productId:100 (100점), productId:50 (50점), ...]
     *
     * 성능:
     * - Time Complexity: O(log N + K) (N=전체 상품수, K=topN)
     * - 매우 빠른 조회 (< 1ms)
     *
     * @param topN 상위 몇 개 (예: 5, 10, 20)
     * @return 상위 N개 상품의 (productId, score) 리스트
     */
    List<RankingItem> getTopProducts(long topN);

    /**
     * 특정 상품의 순위 확인
     *
     * 동작:
     * - 오늘 날짜 기준으로 특정 상품의 순위 반환
     * - 1부터 시작 (1등, 2등, ...)
     *
     * 예: 상품 100번의 순위 조회
     * → getProductRank(100L)
     * → 5 (5등)
     *
     * 만약 상품이 랭킹에 없으면 empty 반환
     * → getProductRank(999L)
     * → empty
     *
     * 성능:
     * - Time Complexity: O(log N)
     * - 매우 빠른 조회 (< 1ms)
     *
     * @param productId 상품 ID
     * @return 순위 (1부터 시작), 없으면 empty
     */
    Optional<Long> getProductRank(Long productId);

    /**
     * 특정 상품의 주문 수 조회
     *
     * @param productId 상품 ID
     * @return 주문 수 (점수 = 주문 수), 없으면 0
     */
    Long getProductScore(Long productId);
}
