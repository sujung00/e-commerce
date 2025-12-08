package com.hhplus.ecommerce.application.ranking;

import com.hhplus.ecommerce.domain.ranking.RankingRepository;
import com.hhplus.ecommerce.domain.ranking.RankingItem;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * RankingServiceImpl - 상품 랭킹 비즈니스 로직 구현
 *
 * 역할:
 * - 일일 상품 주문 랭킹 관리
 * - 실시간 TOP N 상품 조회
 * - 특정 상품 순위 확인
 *
 * 특징:
 * - Redis Sorted Set 활용 (O(log N) 성능)
 * - Atomic 연산으로 동시성 보장
 * - 오늘 날짜 기준으로 자동 처리
 *
 * 사용 흐름:
 * 1. 주문 완료: OrderService → rankingService.incrementProductScore(productId)
 * 2. 인기상품: PopularProductService → rankingService.getTopProducts(5)
 * 3. 상품순위: ProductDetailController → rankingService.getProductRank(productId)
 */
@Service
public class RankingServiceImpl implements RankingService {

    private static final Logger log = LoggerFactory.getLogger(RankingServiceImpl.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingRepository rankingRepository;

    public RankingServiceImpl(RankingRepository rankingRepository) {
        this.rankingRepository = rankingRepository;
    }

    /**
     * 오늘 날짜를 YYYYMMDD 형식의 문자열로 반환
     *
     * @return "20241202" 형식
     */
    private String getTodayDate() {
        return LocalDate.now().format(DATE_FORMATTER);
    }

    @Override
    public void incrementProductScore(Long productId) {
        String todayDate = getTodayDate();

        try {
            rankingRepository.incrementProductScore(todayDate, productId);
            log.debug("[RankingService] 상품 점수 증가: productId={}, date={}", productId, todayDate);
        } catch (Exception e) {
            log.error("[RankingService] 상품 점수 증가 실패: productId={}", productId, e);
            throw new RuntimeException("상품 랭킹 업데이트 실패: " + productId, e);
        }
    }

    @Override
    public List<RankingItem> getTopProducts(long topN) {
        if (topN <= 0) {
            throw new IllegalArgumentException("topN은 1 이상이어야 합니다: " + topN);
        }

        String todayDate = getTodayDate();

        try {
            List<RankingItem> topProducts = rankingRepository.getTopProducts(todayDate, topN);
            log.debug("[RankingService] TOP 상품 조회 완료: topN={}, count={}", topN, topProducts.size());
            return topProducts;
        } catch (Exception e) {
            log.error("[RankingService] TOP 상품 조회 실패: topN={}", topN, e);
            throw new RuntimeException("TOP 상품 조회 실패", e);
        }
    }

    @Override
    public Optional<Long> getProductRank(Long productId) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("유효한 상품 ID가 필요합니다: " + productId);
        }

        String todayDate = getTodayDate();

        try {
            Optional<Long> rank = rankingRepository.getProductRank(todayDate, productId);
            log.debug("[RankingService] 상품 순위 조회: productId={}, rank={}", productId, rank);
            return rank;
        } catch (Exception e) {
            log.error("[RankingService] 상품 순위 조회 실패: productId={}", productId, e);
            throw new RuntimeException("상품 순위 조회 실패", e);
        }
    }

    @Override
    public Long getProductScore(Long productId) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("유효한 상품 ID가 필요합니다: " + productId);
        }

        String todayDate = getTodayDate();

        try {
            Long score = rankingRepository.getProductScore(todayDate, productId);
            log.debug("[RankingService] 상품 주문 수 조회: productId={}, score={}", productId, score);
            return score;
        } catch (Exception e) {
            log.error("[RankingService] 상품 주문 수 조회 실패: productId={}", productId, e);
            throw new RuntimeException("상품 주문 수 조회 실패", e);
        }
    }
}
