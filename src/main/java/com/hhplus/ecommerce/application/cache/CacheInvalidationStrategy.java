package com.hhplus.ecommerce.application.cache;

/**
 * 캐시 무효화 전략 인터페이스
 *
 * 목표:
 * 1. 캐시 무효화 범위를 조건부로 결정 (전체 vs 부분)
 * 2. 배치 처리 시 Lazy 무효화 지원
 * 3. 재고 상태에 따른 선택적 무효화
 *
 * 구현체:
 * - SelectiveCacheInvalidationStrategy: 특정 키만 무효화
 * - LazyCacheInvalidationStrategy: 배치 처리 시 지연 무효화
 * - ConditionalCacheInvalidationStrategy: 조건부 무효화
 */
public interface CacheInvalidationStrategy {

    /**
     * 캐시를 무효화해야 하는지 판단
     *
     * @param context 무효화 컨텍스트 (쿠폰 정보, 재고 상태 등)
     * @return true면 무효화 필요, false면 무효화 불필요
     */
    boolean shouldInvalidate(InvalidationContext context);

    /**
     * 무효화할 캐시 키 결정
     *
     * @param context 무효화 컨텍스트
     * @return 무효화할 캐시 키 배열 (null이면 무효화 안 함)
     */
    String[] getKeysToInvalidate(InvalidationContext context);

    /**
     * 무효화 컨텍스트 클래스
     */
    class InvalidationContext {
        private final Long couponId;
        private final Integer remainingQtyBefore;
        private final Integer remainingQtyAfter;
        private final boolean isLastItem;
        private final boolean isStockExhausted;

        public InvalidationContext(
                Long couponId,
                Integer remainingQtyBefore,
                Integer remainingQtyAfter,
                boolean isLastItem,
                boolean isStockExhausted) {
            this.couponId = couponId;
            this.remainingQtyBefore = remainingQtyBefore;
            this.remainingQtyAfter = remainingQtyAfter;
            this.isLastItem = isLastItem;
            this.isStockExhausted = isStockExhausted;
        }

        public Long getCouponId() {
            return couponId;
        }

        public Integer getRemainingQtyBefore() {
            return remainingQtyBefore;
        }

        public Integer getRemainingQtyAfter() {
            return remainingQtyAfter;
        }

        public boolean isLastItem() {
            return isLastItem;
        }

        public boolean isStockExhausted() {
            return isStockExhausted;
        }
    }
}
