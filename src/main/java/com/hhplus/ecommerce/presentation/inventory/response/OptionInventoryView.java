package com.hhplus.ecommerce.presentation.inventory.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.hhplus.ecommerce.domain.product.ProductOption;

/**
 * OptionInventoryView - Presentation 계층
 *
 * 역할:
 * - ProductOption의 재고 관련 정보만 제공
 * - 상품 재고 조회 응답(InventoryResponse)에 포함될 옵션 정보
 *
 * 필드:
 * - optionId: 옵션 ID
 * - name: 옵션명 (e.g., "사이즈 S", "컬러 검정")
 * - stock: 현재 재고 수량
 * - version: 낙관적 락 버전 (동시성 제어)
 *
 * JSON 예시:
 * {
 *   "option_id": 1,
 *   "name": "사이즈 S",
 *   "stock": 50,
 *   "version": 1
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionInventoryView {
    private Long optionId;
    private String name;
    private Integer stock;
    private Long version;

    /**
     * ProductOption에서 OptionInventoryView로 변환
     *
     * @param option ProductOption 엔티티
     * @return 변환된 OptionInventoryView
     */
    public static OptionInventoryView from(ProductOption option) {
        return OptionInventoryView.builder()
                .optionId(option.getOptionId())
                .name(option.getName())
                .stock(option.getStock())
                .version(option.getVersion())
                .build();
    }
}
