package com.hhplus.ecommerce.infrastructure.cache;

/**
 * ⚠️ [DEPRECATED] 캐시 관련 상수 정의
 *
 * ❌ 이 클래스는 더 이상 사용되지 않습니다.
 * ✅ 대신 {@link com.hhplus.ecommerce.infrastructure.config.RedisKeyType} enum을 사용하세요.
 *
 * 마이그레이션 방법:
 * Before:
 *   CacheConstants.PRODUCT_LIST_CACHE
 *   CacheConstants.COUPON_LIST_CACHE
 *
 * After:
 *   RedisKeyType.CACHE_PRODUCT_LIST
 *   RedisKeyType.CACHE_COUPON_LIST
 *
 * 이 클래스는 하위 호환성을 위해 유지되지만,
 * 새로운 코드에서는 사용하지 마세요.
 *
 * @deprecated 2025-12-03 - {@link com.hhplus.ecommerce.infrastructure.config.RedisKeyType} 사용 권장
 * @see com.hhplus.ecommerce.infrastructure.config.RedisKeyType
 */
@Deprecated(since = "2025-12-03", forRemoval = true)
public final class CacheConstants {

    // ========== Cache Names ==========

    /**
     * 상품 목록 캐시
     * - 사용처: ProductService.getProductList()
     * - TTL: 1시간
     * - Key 형식: list_{page}_{size}_{sort}
     */
    public static final String PRODUCT_LIST_CACHE = "productList";

    /**
     * 상품 상세 캐시
     * - 사용처: ProductService.getProductDetail()
     * - TTL: 2시간
     * - Key 형식: {productId}
     */
    public static final String PRODUCT_DETAIL_CACHE = "productDetail";

    /**
     * 쿠폰 목록 캐시
     * - 사용처: CouponService.getAvailableCoupons()
     * - TTL: 30분
     * - Key 형식: all (고정)
     */
    public static final String COUPON_LIST_CACHE = "couponList";

    /**
     * 인기 상품 캐시
     * - 사용처: PopularProductServiceImpl.getPopularProducts()
     * - TTL: 1시간
     * - Key 형식: list (고정)
     */
    public static final String POPULAR_PRODUCTS_CACHE = "popularProducts";

    /**
     * 장바구니 아이템 캐시
     * - 사용처: (미적용)
     * - TTL: 30분
     */
    public static final String CART_ITEMS_CACHE = "cartItems";

    // ========== Cache Key Patterns ==========

    /**
     * 상품 목록 캐시 키 패턴 (SpEL)
     * - 페이지, 사이즈, 정렬 조건 포함
     * - 예: "list_0_10_price,desc"
     */
    public static final String PRODUCT_LIST_KEY = "'list_' + #page + '_' + #size + '_' + #sort";

    /**
     * 상품 상세 캐시 키 패턴 (SpEL)
     * - 상품 ID 사용
     * - 예: "123"
     */
    public static final String PRODUCT_DETAIL_KEY = "#productId";

    /**
     * 쿠폰 목록 캐시 키 패턴 (SpEL)
     * - 고정 키
     * - 예: "all"
     */
    public static final String COUPON_LIST_KEY = "'all'";

    /**
     * 인기 상품 캐시 키 패턴 (SpEL)
     * - 고정 키
     * - 예: "list"
     */
    public static final String POPULAR_PRODUCTS_KEY = "'list'";

    // ========== TTL Values (seconds) ==========

    public static final long PRODUCT_LIST_TTL_SECONDS = 3600;        // 1시간
    public static final long PRODUCT_DETAIL_TTL_SECONDS = 7200;      // 2시간
    public static final long COUPON_LIST_TTL_SECONDS = 1800;         // 30분
    public static final long POPULAR_PRODUCTS_TTL_SECONDS = 3600;    // 1시간
    public static final long CART_ITEMS_TTL_SECONDS = 1800;          // 30분
    public static final long DEFAULT_TTL_SECONDS = 600;              // 10분

    private CacheConstants() {
        // Utility class, no instantiation
    }
}
