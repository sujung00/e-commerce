package com.hhplus.ecommerce.application.coupon.listener;

import com.hhplus.ecommerce.application.cache.CacheInvalidationStrategy;
import com.hhplus.ecommerce.domain.coupon.event.CouponIssuedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 쿠폰 이벤트 리스너
 *
 * 역할: 쿠폰 발급 완료 후 캐시 무효화를 트랜잭션 외부에서 처리
 *
 * 트랜잭션 분리 이유:
 * - 캐시 무효화는 Redis 외부 I/O 작업
 * - 트랜잭션 내부에서 실행 시 성능 저하 및 트랜잭션 지연
 * - 캐시 무효화 실패가 비즈니스 트랜잭션에 영향을 주지 않아야 함
 *
 * 이벤트 처리 시점: AFTER_COMMIT
 * - 트랜잭션 커밋 성공 후에만 실행
 * - 롤백 시 이벤트 미발행으로 캐시 정합성 보장
 */
@Component
public class CouponEventListener {

    private static final Logger log = LoggerFactory.getLogger(CouponEventListener.class);

    private final CacheManager cacheManager;
    private final CacheInvalidationStrategy cacheInvalidationStrategy;

    public CouponEventListener(CacheManager cacheManager,
                               CacheInvalidationStrategy cacheInvalidationStrategy) {
        this.cacheManager = cacheManager;
        this.cacheInvalidationStrategy = cacheInvalidationStrategy;
    }

    /**
     * 쿠폰 발급 완료 이벤트 리스너
     *
     * 트랜잭션 커밋 후 캐시 무효화 처리
     * - 쿠폰 상세 캐시: 항상 무효화
     * - 활성 쿠폰 목록 캐시: 재고 소진 시만 무효화
     *
     * 실패 처리:
     * - 캐시 무효화 실패는 로깅만 하고 예외를 전파하지 않음
     * - 비즈니스 트랜잭션은 이미 성공했으므로 캐시는 TTL로 자동 만료됨
     *
     * @param event 쿠폰 발급 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCouponIssued(CouponIssuedEvent event) {
        log.info("[CouponEventListener] 쿠폰 발급 이벤트 수신 - couponId={}, userId={}, remainingQty={}",
                event.getCouponId(), event.getUserId(), event.getRemainingQtyAfter());

        try {
            // 캐시 무효화 컨텍스트 생성
            CacheInvalidationStrategy.InvalidationContext context =
                    new CacheInvalidationStrategy.InvalidationContext(
                            event.getCouponId(),
                            event.getRemainingQtyBefore(),
                            event.getRemainingQtyAfter(),
                            event.isLastItem(),
                            event.isStockExhausted()
                    );

            // 전략에 따라 무효화할 캐시 키 결정
            if (cacheInvalidationStrategy.shouldInvalidate(context)) {
                String[] keysToInvalidate = cacheInvalidationStrategy.getKeysToInvalidate(context);

                if (keysToInvalidate != null && keysToInvalidate.length > 0) {
                    for (String key : keysToInvalidate) {
                        try {
                            org.springframework.cache.Cache cache = cacheManager.getCache("couponListCache");
                            if (cache != null) {
                                cache.evict(key);
                                log.info("[CouponEventListener] 캐시 무효화 성공 - key={}", key);
                            }
                        } catch (Exception e) {
                            log.warn("[CouponEventListener] 캐시 무효화 실패 (무시됨) - key={}, error={}",
                                    key, e.getMessage(), e);
                        }
                    }
                }
            }

            log.info("[CouponEventListener] 쿠폰 발급 이벤트 처리 완료 - couponId={}", event.getCouponId());

        } catch (Exception e) {
            // 캐시 무효화 실패는 로깅만 하고 예외를 전파하지 않음
            // 비즈니스 트랜잭션은 이미 성공했으므로 캐시는 TTL로 자동 만료됨
            log.error("[CouponEventListener] 쿠폰 발급 이벤트 처리 중 오류 (무시됨) - couponId={}, error={}",
                    event.getCouponId(), e.getMessage(), e);
        }
    }
}