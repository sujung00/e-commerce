package com.hhplus.ecommerce.domain.product.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 재고 부족 이벤트
 * 상품 옵션의 재고가 임계값 이하로 떨어졌을 때 발행되는 도메인 이벤트
 */
@Getter
@AllArgsConstructor
@ToString
public class LowInventoryEvent {
    private final Long productId;
    private final Long optionId;
    private final String productName;
    private final String optionName;
    private final Integer currentStock;
    private final Integer threshold;
    private final LocalDateTime occurredAt;

    public LowInventoryEvent(Long productId, Long optionId, String productName, String optionName, Integer currentStock, Integer threshold) {
        this.productId = productId;
        this.optionId = optionId;
        this.productName = productName;
        this.optionName = optionName;
        this.currentStock = currentStock;
        this.threshold = threshold;
        this.occurredAt = LocalDateTime.now();
    }
}