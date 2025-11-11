package com.hhplus.ecommerce.presentation.product.response;

import lombok.*;

import java.util.List;

/**
 * 인기 상품 조회 (GET /products/popular) 응답 DTO
 * 상위 5개 인기 상품 목록 + 순위 정보 포함
 */
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopularProductListResponse {
    private List<PopularProductView> products;
}
