package com.hhplus.ecommerce.application;

import com.hhplus.ecommerce.dto.PopularProductListResponse;

/**
 * PopularProductService - 인기 상품 조회 비즈니스 로직 (Application 계층)
 * 캐싱 처리 및 비즈니스 로직 조정
 */
public interface PopularProductService {
    /**
     * 인기 상품 조회 (상위 5개, 최근 3일 주문 수량 기준)
     * 1시간 TTL 캐싱 적용
     *
     * @return 상위 5개 인기 상품 목록
     */
    PopularProductListResponse getPopularProducts();
}