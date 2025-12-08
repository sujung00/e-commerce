package com.hhplus.ecommerce.infrastructure.ranking;

import com.hhplus.ecommerce.domain.ranking.RankingRepository;
import com.hhplus.ecommerce.domain.ranking.RankingItem;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RedisRankingRepository - Redis Sorted Set 기반 구현
 *
 * 설계:
 * - Data Structure: Redis Sorted Set (ZSET)
 * - Key Format: "ranking:daily:{YYYYMMDD}"
 * - Member: productId (상품 ID)
 * - Score: 주문 수 (double 타입, 내림차순 정렬)
 *
 * 특징:
 * - O(log N) 시간 복잡도로 매우 빠른 순위 계산
 * - Atomic 연산: Redis의 단일 스레드 모델로 데이터 경쟁 조건 없음
 * - 분산 환경: 여러 서버 인스턴스에서 공유 가능 (중앙화된 Redis)
 *
 * Redis Commands:
 * - ZADD: 멤버의 점수 증가
 * - ZRANGE: 범위별 조회 (낮은 점수부터)
 * - ZREVRANGE: 범위별 조회 (높은 점수부터)
 * - ZREVRANK: 역순 순위 조회 (높은 점수 기준)
 * - ZSCORE: 멤버의 점수 조회
 * - DEL: 키 삭제
 */
@Repository
public class RedisRankingRepository implements RankingRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisRankingRepository.class);
    private static final String RANKING_KEY_PREFIX = "ranking:daily:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RedisTemplate<String, String> redisTemplate;

    public RedisRankingRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 오늘 날짜를 기준으로 랭킹 키 생성
     *
     * @return "ranking:daily:YYYYMMDD" 형식의 키
     */
    private String getRankingKey() {
        return RANKING_KEY_PREFIX + LocalDate.now().format(DATE_FORMATTER);
    }

    /**
     * 지정된 날짜를 기준으로 랭킹 키 생성
     *
     * @param date YYYYMMDD 형식의 날짜 문자열
     * @return "ranking:daily:YYYYMMDD" 형식의 키
     */
    private String getRankingKey(String date) {
        return RANKING_KEY_PREFIX + date;
    }

    @Override
    public void incrementProductScore(String date, Long productId) {
        String key = getRankingKey(date);
        String member = String.valueOf(productId);

        try {
            // ZADD ranking:daily:YYYYMMDD productId 1
            // - 해당 멤버가 없으면 score=1로 추가
            // - 해당 멤버가 있으면 score을 1 증가
            // Atomicity: Redis ZADD는 원자적 연산 (분산 환경에서도 안전)
            redisTemplate.opsForZSet().incrementScore(key, member, 1.0);

            log.debug("[RankingRepository] 상품 점수 증가: date={}, productId={}, key={}", date, productId, key);
        } catch (Exception e) {
            log.error("[RankingRepository] 상품 점수 증가 실패: date={}, productId={}", date, productId, e);
            throw new RuntimeException("랭킹 점수 업데이트 실패", e);
        }
    }

    @Override
    public List<RankingItem> getTopProducts(String date, long topN) {
        String key = getRankingKey(date);

        try {
            // ZREVRANGE ranking:daily:YYYYMMDD 0 (topN-1) WITHSCORES
            // - 점수가 높은 순서대로 반환 (내림차순)
            // - WITHSCORES로 점수도 함께 반환
            // Time Complexity: O(log N + K) (N=전체 상품수, K=topN)
            Set<ZSetOperations.TypedTuple<String>> results =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, topN - 1);

            if (results == null || results.isEmpty()) {
                log.debug("[RankingRepository] TOP 상품 조회 결과 없음: date={}, topN={}", date, topN);
                return List.of();
            }

            List<RankingItem> items = results.stream()
                    .map(tuple -> RankingItem.builder()
                            .productId(Long.parseLong(tuple.getValue()))
                            .score(tuple.getScore() != null ? tuple.getScore().longValue() : 0L)
                            .build())
                    .collect(Collectors.toList());

            log.debug("[RankingRepository] TOP 상품 조회 완료: date={}, topN={}, count={}", date, topN, items.size());
            return items;
        } catch (Exception e) {
            log.error("[RankingRepository] TOP 상품 조회 실패: date={}, topN={}", date, topN, e);
            throw new RuntimeException("TOP 상품 조회 실패", e);
        }
    }

    @Override
    public Optional<Long> getProductRank(String date, Long productId) {
        String key = getRankingKey(date);
        String member = String.valueOf(productId);

        try {
            // ZREVRANK ranking:daily:YYYYMMDD productId
            // - 역순(높은 점수부터) 순위 반환 (0부터 시작)
            // - 해당 멤버가 없으면 null 반환
            // Time Complexity: O(log N)
            Long rank = redisTemplate.opsForZSet().reverseRank(key, member);

            if (rank == null) {
                log.debug("[RankingRepository] 상품이 랭킹에 없음: date={}, productId={}", date, productId);
                return Optional.empty();
            }

            // rank는 0부터 시작하므로 1을 더해서 반환 (1등, 2등, ...)
            long actualRank = rank + 1;
            log.debug("[RankingRepository] 상품 순위 조회: date={}, productId={}, rank={}", date, productId, actualRank);
            return Optional.of(actualRank);
        } catch (Exception e) {
            log.error("[RankingRepository] 상품 순위 조회 실패: date={}, productId={}", date, productId, e);
            throw new RuntimeException("상품 순위 조회 실패", e);
        }
    }

    @Override
    public Long getProductScore(String date, Long productId) {
        String key = getRankingKey(date);
        String member = String.valueOf(productId);

        try {
            // ZSCORE ranking:daily:YYYYMMDD productId
            // - 해당 멤버의 점수 반환
            // - 해당 멤버가 없으면 null 반환
            Double score = redisTemplate.opsForZSet().score(key, member);

            long result = score != null ? score.longValue() : 0L;
            log.debug("[RankingRepository] 상품 점수 조회: date={}, productId={}, score={}", date, productId, result);
            return result;
        } catch (Exception e) {
            log.error("[RankingRepository] 상품 점수 조회 실패: date={}, productId={}", date, productId, e);
            throw new RuntimeException("상품 점수 조회 실패", e);
        }
    }

    @Override
    public void resetDailyRanking(String date) {
        String key = getRankingKey(date);

        try {
            // DEL ranking:daily:YYYYMMDD
            // - 해당 키의 모든 데이터 삭제
            // - 새로운 날짜 시작 시 호출
            Boolean deleted = redisTemplate.delete(key);
            log.info("[RankingRepository] 일일 랭킹 초기화: date={}, deleted={}", date, deleted);
        } catch (Exception e) {
            log.error("[RankingRepository] 일일 랭킹 초기화 실패: date={}", date, e);
            throw new RuntimeException("랭킹 초기화 실패", e);
        }
    }
}
