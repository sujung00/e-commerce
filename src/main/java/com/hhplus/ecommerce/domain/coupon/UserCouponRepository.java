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
     * 사용자의 모든 쿠폰 조회
     */
    List<UserCoupon> findByUserId(Long userId);

    /**
     * 사용자 쿠폰 업데이트
     */
    UserCoupon update(UserCoupon userCoupon);
}
