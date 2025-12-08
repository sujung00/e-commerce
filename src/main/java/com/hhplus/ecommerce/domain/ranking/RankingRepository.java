package com.hhplus.ecommerce.domain.ranking;

import java.util.List;
import java.util.Optional;

/**
 * RankingRepository - 상품 랭킹 저장소 Port Interface
 *
 * Redis Sorted Set 기반 실시간 주문 랭킹 관리
 *
 * 설계:
 * - Key: "ranking:daily:{YYYYMMDD}" (일일 랭킹)
 * - Member: productId (상품 ID)
 * - Score: 주문 수 (내림차순 정렬)
 *
 * 특징:
 * - Atomic 연산: ZADD, ZRANGE 등 Redis 원자성 보장
 * - 동시성 안전: Redis의 단일 스레드 모델로 데이터 경쟁 조건 없음
 * - O(log N) 성능: Sorted Set의 효율적 순위 계산
 */
public interface RankingRepository {

    /**
     * 상품 점수 증가 (주문 발생 시)
     *
     * 동작:
     * - ZADD ranking:daily:YYYYMMDD productId 1 으로 증가
     * - 해당 상품이 없으면 새로 생성하고 score=1로 설정
     * - 기존 상품이면 score 1 증가
     *
     * Atomicity:
     * - Redis ZADD는 원자적 연산 (분산 환경에서도 안전)
     * - 여러 스레드/프로세스의 동시 접근도 순서대로 처리
     *
     * @param date 날짜 (YYYYMMDD 형식)
     * @param productId 상품 ID
     */
    void incrementProductScore(String date, Long productId);

    /**
     * TOP N 상품 조회 (상위 순위)
     *
     * 동작:
     * - ZRANGE ranking:daily:YYYYMMDD 0 (n-1) WITHSCORES
     * - 점수가 높은 순서대로 반환 (내림차순)
     * - 동일 점수면 상품 ID 순서 유지
     *
     * 성능:
     * - Time Complexity: O(log N + K) (N=전체 상품수, K=topN)
     * - 매우 빠른 조회 (일반적으로 < 1ms)
     *
     * @param date 날짜 (YYYYMMDD 형식)
     * @param topN 상위 몇 개 (예: 10)
     * @return 상위 N개 상품의 (productId, score) 리스트
     */
    List<RankingItem> getTopProducts(String date, long topN);

    /**
     * 특정 상품의 순위 확인
     *
     * 동작:
     * - ZREVRANK ranking:daily:YYYYMMDD productId
     * - 해당 상품의 순위를 0부터 시작하여 반환
     * - 상품이 없으면 empty 반환
     *
     * 성능:
     * - Time Complexity: O(log N)
     * - 매우 빠른 조회
     *
     * @param date 날짜 (YYYYMMDD 형식)
     * @param productId 상품 ID
     * @return 순위 (1부터 시작), 없으면 empty
     */
    Optional<Long> getProductRank(String date, Long productId);

    /**
     * 특정 상품의 점수 조회
     *
     * @param date 날짜 (YYYYMMDD 형식)
     * @param productId 상품 ID
     * @return 점수, 없으면 0
     */
    Long getProductScore(String date, Long productId);

    /**
     * 일일 랭킹 초기화 (새로운 날짜 시작)
     *
     * 동작:
     * - DEL ranking:daily:YYYYMMDD (기존 데이터 삭제)
     * - 새로운 날짜의 랭킹 시작
     *
     * @param date 날짜 (YYYYMMDD 형식)
     */
    void resetDailyRanking(String date);
}
