package com.hhplus.ecommerce.infrastructure.lock;

/**
 * 분산락 키 생성 유틸리티
 *
 * 설계 원칙:
 * - 패턴: resource_type:resource_id[:additional_id]
 * - 자원 단위로 키 생성 (충돌 지점 중심)
 * - 일관된 명명으로 키 충돌 방지
 *
 * 사용 예:
 * - @DistributedLock(key = LockKeyGenerator.PRODUCT_STOCK_KEY_TEMPLATE)
 *   private void deductStock(Long productId, Long optionId) { ... }
 *
 * - 또는 프로그래밍 방식:
 *   String lockKey = LockKeyGenerator.productStock(productId, optionId);
 */
public class LockKeyGenerator {

    // ============ Spring EL 템플릿 (어노테이션용) ============

    /**
     * 상품 재고 차감용 락 키 템플릿
     * 사용: @DistributedLock(key = LockKeyGenerator.PRODUCT_STOCK_KEY_TEMPLATE)
     * 예: deductStock(productId=1, optionId=2) → "product:stock:1:2"
     */
    public static final String PRODUCT_STOCK_KEY_TEMPLATE =
            "'product:stock:' + #p0 + ':' + #p1";

    /**
     * 사용자 잔액 차감용 락 키 템플릿
     * 사용: @DistributedLock(key = LockKeyGenerator.USER_BALANCE_KEY_TEMPLATE)
     * 예: deductBalance(userId=10) → "user:balance:10"
     */
    public static final String USER_BALANCE_KEY_TEMPLATE =
            "'user:balance:' + #p0";

    /**
     * 쿠폰 재고 차감용 락 키 템플릿
     * 사용: @DistributedLock(key = LockKeyGenerator.COUPON_STOCK_KEY_TEMPLATE)
     * 예: issueCoupon(couponId=5) → "coupon:stock:5"
     */
    public static final String COUPON_STOCK_KEY_TEMPLATE =
            "'coupon:stock:' + #p0";

    // ============ 프로그래밍 방식 (동적 생성용) ============

    /**
     * 상품 재고 차감용 락 키 생성
     *
     * @param productId 상품 ID
     * @param optionId 옵션 ID
     * @return 락 키 (예: "product:stock:1:2")
     */
    public static String productStock(Long productId, Long optionId) {
        return "product:stock:" + productId + ":" + optionId;
    }

    /**
     * 사용자 잔액 차감용 락 키 생성
     *
     * @param userId 사용자 ID
     * @return 락 키 (예: "user:balance:10")
     */
    public static String userBalance(Long userId) {
        return "user:balance:" + userId;
    }

    /**
     * 쿠폰 재고 발급용 락 키 생성
     *
     * @param couponId 쿠폰 ID
     * @return 락 키 (예: "coupon:stock:5")
     */
    public static String couponStock(Long couponId) {
        return "coupon:stock:" + couponId;
    }

    // ============ 비공개 생성자 (Utility 클래스) ============

    private LockKeyGenerator() {
        throw new AssertionError("이 클래스는 인스턴스화될 수 없습니다");
    }
}
