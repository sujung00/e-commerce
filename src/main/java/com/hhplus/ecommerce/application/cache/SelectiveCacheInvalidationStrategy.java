package com.hhplus.ecommerce.application.cache;

import com.hhplus.ecommerce.infrastructure.config.RedisKeyType;
import org.springframework.stereotype.Component;

/**
 * 선택적 캐시 무효화 전략
 *
 * 개별 쿠폰 발급 시 사용:
 * 1. 쿠폰 상세 캐시만 무효화 (CACHE_COUPON_DETAIL)
 * 2. 재고 소진 시만 활성 쿠폰 목록 캐시 무효화 (CACHE_ACTIVE_COUPONS)
 * 3. 전체 쿠폰 목록 캐시는 필요할 때만 무효화
 *
 * 효과:
 * - 불필요한 캐시 무효화 감소 (allEntries=true 제거)
 * - 다른 사용자의 쿠폰 발급에 영향 최소화
 * - 캐시 히트율 유지
 */
@Component
public class SelectiveCacheInvalidationStrategy implements CacheInvalidationStrategy {

    @Override
    public boolean shouldInvalidate(InvalidationContext context) {
        // 항상 선택적 무효화를 수행 (조건부 결정은 getKeysToInvalidate에서)
        return true;
    }

    @Override
    public String[] getKeysToInvalidate(InvalidationContext context) {
        // 1. 쿠폰 상세 캐시는 항상 무효화
        String couponDetailKey = RedisKeyType.CACHE_COUPON_DETAIL
                .buildKey(context.getCouponId());

        // 2. 재고 소진 시만 활성 쿠폰 목록 캐시 무효화
        if (context.isStockExhausted()) {
            String activeCouponsKey = RedisKeyType.CACHE_ACTIVE_COUPONS.getKey();
            return new String[]{couponDetailKey, activeCouponsKey};
        }

        // 3. 재고가 남아있으면 상세 캐시만 무효화
        return new String[]{couponDetailKey};
    }
}
