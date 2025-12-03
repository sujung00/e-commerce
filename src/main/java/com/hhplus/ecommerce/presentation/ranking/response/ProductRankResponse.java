package com.hhplus.ecommerce.presentation.ranking.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * ProductRankResponse - 특정 상품 순위 조회 응답 DTO
 *
 * 응답 형식 (랭킹에 있는 경우):
 * {
 *   "productId": 100,
 *   "rank": 5,
 *   "score": 150,
 *   "message": null
 * }
 *
 * 응답 형식 (랭킹에 없는 경우):
 * {
 *   "productId": 100,
 *   "rank": null,
 *   "score": 0,
 *   "message": "상품이 랭킹에 없습니다"
 * }
 *
 * 에러 응답 형식:
 * {
 *   "productId": 100,
 *   "rank": null,
 *   "score": 0,
 *   "message": "상품 ID는 1 이상이어야 합니다"
 * }
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductRankResponse {
    @JsonProperty("product_id")
    private final Long productId;
    private final Long rank;
    private final Long score;
    private final String message;

    /**
     * 정상 응답 생성 (순위 있는 경우, 메시지 없음)
     *
     * @param productId 상품 ID
     * @param rank 순위 (1부터 시작)
     * @param score 주문 수
     * @return ProductRankResponse
     */
    public static ProductRankResponse of(Long productId, Long rank, Long score) {
        return of(productId, rank, score, null);
    }

    /**
     * 응답 생성 (순위 여부와 메시지 포함)
     *
     * @param productId 상품 ID
     * @param rank 순위 (없으면 null)
     * @param score 주문 수
     * @param message 메시지 (없으면 null)
     * @return ProductRankResponse
     */
    public static ProductRankResponse of(Long productId, Long rank, Long score, String message) {
        return ProductRankResponse.builder()
                .productId(productId)
                .rank(rank)
                .score(score)
                .message(message)
                .build();
    }
}
