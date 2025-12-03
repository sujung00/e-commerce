package com.hhplus.ecommerce.infrastructure.config;

/**
 * ⚠️ [DEPRECATED] 캐시 키 상수 클래스
 *
 * ❌ 이 클래스는 더 이상 사용되지 않습니다.
 * ✅ 대신 {@link RedisKeyType} enum을 사용하세요.
 *
 * 마이그레이션 방법:
 * Before:
 *   CacheKeyConstants.PRODUCT_LIST
 *   CacheKeyConstants.COUPON_LIST
 *
 * After:
 *   RedisKeyType.CACHE_PRODUCT_LIST
 *   RedisKeyType.CACHE_COUPON_LIST
 *
 * 이 클래스는 하위 호환성을 위해 유지되지만,
 * 새로운 코드에서는 사용하지 마세요.
 *
 * @deprecated 2025-12-03 - {@link RedisKeyType} 사용 권장
 * @see RedisKeyType
 */
@Deprecated(since = "2025-12-03", forRemoval = true)
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
