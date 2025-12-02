package com.hhplus.ecommerce.infrastructure.config;

/**
 * 캐시 키 상수 클래스
 *
 * 역할:
 * - @Cacheable의 문자열 리터럴을 상수로 관리
 * - 캐시 키 일관성 보장
 * - IDE 자동완성으로 개발 효율 향상
 * - 캐시 키 변경 시 한 곳에서만 수정
 *
 * 캐시 전략:
 * - productList: 상품 목록 조회 (TTL: 1시간)
 * - productDetail: 상품 상세 조회 (TTL: 2시간)
 * - couponList: 쿠폰 목록 조회 (TTL: 30분)
 * - popularProducts: 인기 상품 조회 (TTL: 1시간)
 * - cartItems: 장바구니 아이템 (TTL: 30분)
 */
public final class CacheKeyConstants {

    private CacheKeyConstants() {
        throw new AssertionError("Instantiation not allowed");
    }

    // ===== 상품 관련 캐시 =====

    /**
     * 상품 목록 캐시 이름
     * 사용: @Cacheable(value = CacheKeyConstants.PRODUCT_LIST)
     * TTL: 1시간
     * 빈도: 매우 높음
     */
    public static final String PRODUCT_LIST = "productList";

    /**
     * 상품 상세 정보 캐시 이름
     * 사용: @Cacheable(value = CacheKeyConstants.PRODUCT_DETAIL)
     * TTL: 2시간
     * 빈도: 높음
     */
    public static final String PRODUCT_DETAIL = "productDetail";

    /**
     * 인기 상품 캐시 이름
     * 사용: @Cacheable(value = CacheKeyConstants.POPULAR_PRODUCTS)
     * TTL: 1시간
     * 빈도: 높음
     */
    public static final String POPULAR_PRODUCTS = "popularProducts";

    // ===== 쿠폰 관련 캐시 =====

    /**
     * 쿠폰 목록 캐시 이름
     * 사용: @Cacheable(value = CacheKeyConstants.COUPON_LIST)
     * TTL: 30분
     * 빈도: 높음
     */
    public static final String COUPON_LIST = "couponList";

    // ===== 장바구니 관련 캐시 =====

    /**
     * 장바구니 아이템 캐시 이름
     * 사용: @Cacheable(value = CacheKeyConstants.CART_ITEMS)
     * TTL: 30분
     * 빈도: 중간
     */
    public static final String CART_ITEMS = "cartItems";
}
