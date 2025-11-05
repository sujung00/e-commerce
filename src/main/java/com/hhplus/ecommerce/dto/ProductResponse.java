package com.hhplus.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * 상품 목록/상세 조회 응답에서 상품 기본 정보를 담는 DTO
 */
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

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getPrice() {
        return price;
    }

    public void setPrice(Long price) {
        this.price = price;
    }

    public Integer getTotalStock() {
        return totalStock;
    }

    public void setTotalStock(Integer totalStock) {
        this.totalStock = totalStock;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}