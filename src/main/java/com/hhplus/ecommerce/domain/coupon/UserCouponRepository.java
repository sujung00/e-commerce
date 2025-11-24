package com.hhplus.ecommerce.domain.coupon;

import java.util.List;
import java.util.Optional;

/**
 * UserCouponRepository - Domain 계층 (Port)
 *
 * 역할:
 * - 사용자 쿠폰 발급 기록 영속성 추상화
 * - Infrastructure 계층이 구현해야 할 인터페이스
 *
 * 참고: Port-Adapter 패턴
 * - 이 인터페이스는 Port (Domain이 정의)
 * - Infrastructure의 InMemoryUserCouponRepository가 Adapter (구현)
 */
public interface UserCouponRepository {
    /**
     * 사용자 쿠폰 발급 기록 저장
     */
    UserCoupon save(UserCoupon userCoupon);

    /**
     * 사용자 쿠폰 ID로 조회
     */
    Optional<UserCoupon> findById(Long userCouponId);

    /**
     * 사용자별 쿠폰 조회 (status별 필터링)
     */
    List<UserCoupon> findByUserIdAndStatus(Long userId, String status);

    /**
     * 사용자가 특정 쿠폰을 이미 발급받았는지 확인
     * UNIQUE(user_id, coupon_id) 검증 용도
     */
    Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId);

    /**
     * 비관적 락을 사용하여 사용자 쿠폰 조회
     * SELECT ... FOR UPDATE로 즉시 락 획득
     *
     * 용도: 쿠폰 사용 시 TOCTOU 문제 방지
     * 특징:
     * - 즉시 락 획득으로 쿠폰 중복 사용 방지
     * - 다중 프로세스 환경에서도 안전
     * - DB 레벨에서 동시성 보장
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 비관적 락이 적용된 UserCoupon
     */
    Optional<UserCoupon> findByUserIdAndCouponIdForUpdate(Long userId, Long couponId);

    /**
     * 사용자의 모든 쿠폰 조회
     */
    List<UserCoupon> findByUserId(Long userId);

    /**
     * 사용자 쿠폰 업데이트
     */
    UserCoupon update(UserCoupon userCoupon);
}
