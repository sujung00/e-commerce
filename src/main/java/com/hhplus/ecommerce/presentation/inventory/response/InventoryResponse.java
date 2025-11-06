package com.hhplus.ecommerce.presentation.inventory.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.hhplus.ecommerce.domain.product.Product;

import java.util.List;

/**
 * InventoryResponse - Presentation 계층
 *
 * 역할:
 * - 5.1 상품 재고 현황 조회 API의 응답 DTO
 * - 상품의 전체 재고 및 옵션별 재고 정보 제공
 *
 * API 명세 (api-specification.md 5.1):
 * - GET /api/inventory/{product_id}
 * - 응답: 상품 ID, 상품명, 전체 재고, 옵션 배열
 *
 * 필드:
 * - productId: 상품 ID
 * - productName: 상품명
 * - totalStock: 전체 재고 (모든 옵션의 재고 합계)
 * - options: 옵션 배열 (OptionInventoryView 리스트)
 *
 * JSON 예시:
 * {
 *   "product_id": 1,
 *   "product_name": "특급 우육 300g",
 *   "total_stock": 450,
 *   "options": [
 *     {
 *       "option_id": 1,
 *       "name": "사이즈 S",
 *       "stock": 100,
 *       "version": 1
 *     },
 *     {
 *       "option_id": 2,
 *       "name": "사이즈 M",
 *       "stock": 150,
 *       "version": 1
 *     }
 *   ]
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {
    @JsonProperty("product_id")
    private Long productId;

    @JsonProperty("product_name")
    private String productName;

    @JsonProperty("total_stock")
    private Integer totalStock;

    private List<OptionInventoryView> options;

    /**
     * Product와 옵션 목록에서 InventoryResponse로 변환
     *
     * @param product Product 엔티티
     * @param options ProductOption 리스트
     * @return 변환된 InventoryResponse
     */
    public static InventoryResponse from(Product product, List<OptionInventoryView> options) {
        // 전체 재고 계산 (모든 옵션의 재고 합계)
        Integer totalStock = options.stream()
                .mapToInt(OptionInventoryView::getStock)
                .sum();

        return InventoryResponse.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .totalStock(totalStock)
                .options(options)
                .build();
    }
}
