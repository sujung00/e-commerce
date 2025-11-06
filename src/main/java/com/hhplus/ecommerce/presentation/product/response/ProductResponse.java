package com.hhplus.ecommerce.presentation.product.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 상품 목록/상세 조회 응답에서 상품 기본 정보를 담는 DTO
 */
@Setter
@Getter
public class ProductResponse {
    @JsonProperty("product_id")
    private Long productId;

    @JsonProperty("product_name")
    private String productName;

    private String description;
    private Long price;

    @JsonProperty("total_stock")
    private Integer totalStock;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public ProductResponse() {}

    public ProductResponse(Long productId, String productName, String description,
                          Long price, Integer totalStock, String status, LocalDateTime createdAt) {
        this.productId = productId;
        this.productName = productName;
        this.description = description;
        this.price = price;
        this.totalStock = totalStock;
        this.status = status;
        this.createdAt = createdAt;
    }

}
