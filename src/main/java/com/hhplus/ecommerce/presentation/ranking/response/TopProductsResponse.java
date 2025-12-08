package com.hhplus.ecommerce.presentation.ranking.response;

import com.hhplus.ecommerce.domain.ranking.RankingItem;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * TopProductsResponse - TOP N 상품 조회 응답 DTO
 *
 * 응답 형식:
 * {
 *   "topProducts": [
 *     {"productId": 100, "score": 150},
 *     {"productId": 200, "score": 120},
 *     ...
 *   ],
 *   "message": null
 * }
 *
 * 에러 응답 형식:
 * {
 *   "topProducts": [],
 *   "message": "topN은 1 이상이어야 합니다"
 * }
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TopProductsResponse {
    @JsonProperty("top_products")
    private final List<ProductRankingView> topProducts;
    private final String message;

    /**
     * 정상 응답 생성 (메시지 없음)
     *
     * @param items 랭킹 아이템 리스트
     * @return TopProductsResponse
     */
    public static TopProductsResponse of(List<RankingItem> items) {
        return of(items, null);
    }

    /**
     * 응답 생성 (메시지 포함)
     *
     * @param items 랭킹 아이템 리스트
     * @param message 에러 메시지 (없으면 null)
     * @return TopProductsResponse
     */
    public static TopProductsResponse of(List<RankingItem> items, String message) {
        List<ProductRankingView> views = items.stream()
                .map(item -> ProductRankingView.builder()
                        .productId(item.getProductId())
                        .score(item.getScore())
                        .build())
                .collect(Collectors.toList());

        return TopProductsResponse.builder()
                .topProducts(views)
                .message(message)
                .build();
    }

    /**
     * ProductRankingView - TOP 상품 아이템 뷰
     *
     * {
     *   "productId": 100,
     *   "score": 150
     * }
     */
    @Getter
    @Builder
    public static class ProductRankingView {
        @JsonProperty("product_id")
        private final Long productId;
        private final Long score;
    }
}
