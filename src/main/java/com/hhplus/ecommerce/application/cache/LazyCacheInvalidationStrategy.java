package com.hhplus.ecommerce.application.cache;

import com.hhplus.ecommerce.infrastructure.config.RedisKeyType;

/**
 * 지연(Lazy) 캐시 무효화 전략
 *
 * 배치 처리 시 사용:
 * 1. 개별 발급: 즉시 무효화 (응답 지연 최소화)
 * 2. 배치 발급: 지연 무효화 (배치 완료 후 한 번에 처리)
 *
 * 최적화:
 * - 배치 처리 중 여러 번의 쿠폰 발급 시
 * - 각각 무효화하지 않고 배치 완료 후 한 번에 무효화
 * - DB 쿼리와 캐시 무효화 오버헤드 감소
 * - 배치 처리 성능 향상
 *
 * 사용 예:
 * 1. 배치 시작: strategy = LazyCacheInvalidationStrategy
 * 2. 발급 반복: shouldInvalidate() = false (무효화 지연)
 * 3. 배치 완료: invalidateLazyCaches() 호출 (한 번에 무효화)
 *
 * 현재 상태:
 * - @Component 제거됨 (아직 사용되지 않는 전략, Spring 빈 충돌 제거)
 * - 필요 시 명시적으로 인스턴스 생성하거나 @Bean으로 설정 가능
 */
public class LazyCacheInvalidationStrategy implements CacheInvalidationStrategy {

    private static final int BATCH_THRESHOLD = 10;  // 배치 크기 임계값

    @Override
    public boolean shouldInvalidate(InvalidationContext context) {
        // 지연 무효화: 개별 무효화하지 않음
        // 배치 완료 후 invalidateLazyCaches()에서 한 번에 처리
        return false;
    }

    @Override
    public String[] getKeysToInvalidate(InvalidationContext context) {
        // 지연 무효화이므로 null 반환 (실제 무효화는 배치 완료 후)
        return null;
    }

    /**
     * 배치 처리 완료 후 호출
     * 누적된 쿠폰 발급을 일괄 무효화
     *
     * @param totalIssuedCount 배치에서 발급된 총 개수
     * @param anyStockExhausted 재고가 소진된 쿠폰이 있는지 여부
     * @return 무효화할 캐시 키 배열
     */
    public String[] getKeysForBatchInvalidation(int totalIssuedCount, boolean anyStockExhausted) {
        // 배치 크기가 임계값을 초과하거나 재고가 소진되면 활성 쿠폰 목록도 무효화
        if (totalIssuedCount >= BATCH_THRESHOLD || anyStockExhausted) {
            return new String[]{
                    RedisKeyType.CACHE_ACTIVE_COUPONS.getKey(),
                    RedisKeyType.CACHE_COUPON_LIST.getKey()
            };
        }

        // 소규모 배치: 쿠폰 목록만 무효화 (활성 쿠폰 목록은 유지)
        return new String[]{
                RedisKeyType.CACHE_COUPON_LIST.getKey()
        };
    }
}
