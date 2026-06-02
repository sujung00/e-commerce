package com.hhplus.ecommerce.application.coupon;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponNotFoundException;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.infrastructure.constants.RetryProperties;
import com.hhplus.ecommerce.presentation.coupon.response.AvailableCouponResponse;
import com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse;
import com.hhplus.ecommerce.presentation.coupon.response.UserCouponResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CouponService - 쿠폰 비즈니스 흐름 조정 (Application 계층)
 *
 * 역할:
 * - 사용자 검증, 재시도 정책, 캐시 조회 등 흐름 조정
 * - DB 트랜잭션 경계는 CouponTransactionService 에 위임
 *
 * 트랜잭션 분리 설계:
 * - issueCoupon() 에서 this.issueCouponWithLock() 를 직접 호출하면 Spring AOP 프록시를
 *   우회하여 @Transactional(REQUIRES_NEW) 가 적용되지 않는다 (self-invocation 문제).
 * - CouponTransactionService 를 별도 빈으로 분리함으로써 프록시 경유 호출이 보장된다.
 * - OrderService → OrderTransactionService 와 동일한 패턴.
 *
 * 외부 API (issueCouponWithLock, revertCouponToAvailable) 는
 * CouponQueueService, CouponIssueConsumer, CompensationService 의 기존 호출을
 * 깨지 않기 위해 thin delegator 로 유지한다.
 */
@Service
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final RetryProperties retryProperties;
    private final CouponTransactionService couponTransactionService;

    public CouponService(CouponRepository couponRepository,
                         UserCouponRepository userCouponRepository,
                         UserRepository userRepository,
                         RetryProperties retryProperties,
                         CouponTransactionService couponTransactionService) {
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.userRepository = userRepository;
        this.retryProperties = retryProperties;
        this.couponTransactionService = couponTransactionService;
    }

    /**
     * 쿠폰 발급 (선착순) - POST /coupons/issue
     *
     * 흐름:
     * 1. 사용자 존재 검증
     * 2. couponTransactionService.issueCouponWithLock() 호출 (Spring 프록시 경유)
     *    → @Transactional(REQUIRES_NEW) 가 실제로 적용됨
     *    → 커밋 후 @TransactionalEventListener(AFTER_COMMIT) 정상 동작
     * 3. 시스템 오류 시 exponential backoff 재시도
     *
     * 실측 결과 (ab -n 5000 -c 200, MySQL 8.0 docker-compose, JVM 완전 워밍업 후, 2026-06-02):
     * ┌─────────────────────┬─────────────────────────────────────────────────────────────┐
     * │ 항목                │ 측정값                                                      │
     * ├─────────────────────┼─────────────────────────────────────────────────────────────┤
     * │ HTTP TPS            │ ~1,220 req/s (M1~M3 평균: 895 → 1,261 → 1,503 req/s)       │
     * │ 처리 TPS            │ ~1,220 req/s (= HTTP TPS — 요청·처리 비분리)                │
     * └─────────────────────┴─────────────────────────────────────────────────────────────┘
     * 병목: SELECT FOR UPDATE 직렬화 + REQUIRES_NEW 중첩 커넥션 (2개/요청)
     * vs Redis Queue HTTP TPS (~5,050 req/s): 4.1배 낮음
     * vs Kafka      HTTP TPS (~8,200 req/s): 6.7배 낮음
     * 주의: HTTP TPS = 처리 TPS (비동기와 달리 요청·처리 분리 없음)
     *       HikariCP 기본 풀(10개) 설정 시 c=200에서 커넥션 고갈 → 최소 풀 450 이상 권장
     */
    @Transactional
    public IssueCouponResponse issueCoupon(Long userId, Long couponId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        int maxRetries = retryProperties.getCoupon().getIssuance().getMaxAttempts();
        int retryCount = 0;
        long retryDelayMs = retryProperties.getCoupon().getIssuance().getInitialDelayMs();

        while (retryCount < maxRetries) {
            try {
                return couponTransactionService.issueCouponWithLock(userId, couponId);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    throw e;
                }
                try {
                    Thread.sleep(retryDelayMs);
                    retryDelayMs *= 2;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("쿠폰 발급 중 인터럽트됨", ie);
                }
            }
        }

        throw new IllegalArgumentException("쿠폰 발급 실패: 재시도 횟수 초과");
    }

    /**
     * 쿠폰 발급 (Saga 주문 연관) - CouponTransactionService 에 위임
     *
     * 호출자: Saga 쿠폰 발급 Step
     * 트랜잭션: CouponTransactionService.issueCouponWithLock() 의 REQUIRES_NEW 적용
     */
    public IssueCouponResponse issueCouponWithLock(Long userId, Long couponId, Long orderId) {
        return couponTransactionService.issueCouponWithLock(userId, couponId, orderId);
    }

    /**
     * 쿠폰 발급 (주문 연관 없음) - CouponTransactionService 에 위임
     *
     * 호출자: CouponQueueService, CouponIssueConsumer
     * 트랜잭션: CouponTransactionService.issueCouponWithLock() 의 REQUIRES_NEW 적용
     */
    public IssueCouponResponse issueCouponWithLock(Long userId, Long couponId) {
        return couponTransactionService.issueCouponWithLock(userId, couponId);
    }

    /**
     * 쿠폰 상태 복구 (Saga 보상) - CouponTransactionService 에 위임
     *
     * 호출자: CompensationService
     * 트랜잭션: CouponTransactionService.revertCouponToAvailable() 의 REQUIRES_NEW 적용
     */
    public void revertCouponToAvailable(Long userId, Long couponId) {
        couponTransactionService.revertCouponToAvailable(userId, couponId);
    }

    // ───────────────────────────────────────────────────────────────────────────
    // 조회 메서드 (트랜잭션 경계 불필요, 캐시 활용)
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * 사용자가 보유한 쿠폰 조회 - GET /coupons/issued?status=UNUSED
     *
     * 캐시 전략: userCouponsCache, key = "userId:status", TTL 5분
     */
    @Cacheable(
            value = "userCouponsCache",
            key = "#userId + ':' + (#status != null && !#status.isEmpty() ? #status : 'UNUSED')",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<UserCouponResponse> getUserCoupons(Long userId, String status) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        String statusStr = status != null && !status.isEmpty() ? status : "UNUSED";
        UserCouponStatus.from(statusStr); // enum 유효성 검증

        List<UserCoupon> userCoupons = userCouponRepository.findByUserIdAndStatus(userId, statusStr);

        return userCoupons.stream()
                .map(uc -> {
                    Coupon coupon = couponRepository.findById(uc.getCouponId())
                            .orElseThrow(() -> new CouponNotFoundException(uc.getCouponId()));
                    return UserCouponResponse.from(uc, coupon);
                })
                .collect(Collectors.toList());
    }

    /**
     * 발급 가능한 쿠폰 조회 - GET /coupons
     *
     * 캐시 전략: couponListCache, key = "cache:coupon:list", TTL 30분
     */
    @Cacheable(value = "couponListCache", key = "'cache:coupon:list'")
    public List<AvailableCouponResponse> getAvailableCoupons() {
        return couponRepository.findAllAvailable().stream()
                .map(AvailableCouponResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 캐시에서 특정 쿠폰 조회 (CouponController 검증용)
     */
    public AvailableCouponResponse getAvailableCouponFromCache(Long couponId) {
        try {
            return getAvailableCoupons().stream()
                    .filter(c -> c.getCouponId().equals(couponId))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("[CouponService] 캐시에서 쿠폰 조회 실패: couponId={}", couponId, e);
            return null;
        }
    }

    /**
     * 쿠폰 발급 완료 건수 조회 (Consumer TPS 측정용)
     */
    public long getIssuedCountByCouponId(Long couponId) {
        return userCouponRepository.countByCouponId(couponId);
    }
}
