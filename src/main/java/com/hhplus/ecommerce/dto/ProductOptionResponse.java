package com.hhplus.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 상품 상세 조회 응답에서 옵션 정보를 담는 DTO
 */
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

    public Long getOptionId() {
        return optionId;
    }

    public void setOptionId(Long optionId) {
        this.optionId = optionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}