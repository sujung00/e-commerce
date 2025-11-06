package com.hhplus.ecommerce.presentation.product.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 인기 상품 개별 항목 (GET /products/popular 응답)
 * rank: 1~5 순위
 */
@Setter
@Getter
@Builder
@AllArgsConstructor
public class PopularProductView {
    @JsonProperty("product_id")
    private Long productId;

    @JsonProperty("product_name")
    private String productName;

    private Long price;

    @JsonProperty("total_stock")
    private Integer totalStock;

    private String status;

    @JsonProperty("order_count_3days")
    private Long orderCount3Days;

    private Integer rank; // 1~5 순위

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime createdAt;
}
