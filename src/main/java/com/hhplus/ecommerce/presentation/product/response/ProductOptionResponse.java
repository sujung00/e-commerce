package com.hhplus.ecommerce.presentation.product.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 상품 상세 조회 응답에서 옵션 정보를 담는 DTO
 */
@Setter
@Getter
public class ProductOptionResponse {
    @JsonProperty("option_id")
    private Long optionId;

    private String name;
    private Integer stock;
    private Long version;

    public ProductOptionResponse() {}

    public ProductOptionResponse(Long optionId, String name, Integer stock, Long version) {
        this.optionId = optionId;
        this.name = name;
        this.stock = stock;
        this.version = version;
    }

}
