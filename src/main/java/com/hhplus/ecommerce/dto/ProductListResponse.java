package com.hhplus.ecommerce.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 상품 목록 조회 (GET /products) 응답 DTO
 * 페이지네이션 정보와 상품 목록을 포함
 */
@Setter
@Getter
public class ProductListResponse {
    private List<ProductResponse> content;
    private Long totalElements;
    private Long totalPages;
    private Integer currentPage;
    private Integer size;

    public ProductListResponse() {}

    public ProductListResponse(List<ProductResponse> content, Long totalElements,
                              Long totalPages, Integer currentPage, Integer size) {
        this.content = content;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.currentPage = currentPage;
        this.size = size;
    }

}