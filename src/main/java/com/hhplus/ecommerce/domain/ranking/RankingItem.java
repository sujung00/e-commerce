package com.hhplus.ecommerce.domain.ranking;

import lombok.Builder;
import lombok.Getter;

/**
 * RankingItem - 랭킹 항목 (상품 ID + 점수)
 *
 * Redis Sorted Set에서 반환되는 데이터를 도메인 객체로 변환
 */
@Getter
@Builder
public class RankingItem {
    private Long productId;
    private Long score;
}
