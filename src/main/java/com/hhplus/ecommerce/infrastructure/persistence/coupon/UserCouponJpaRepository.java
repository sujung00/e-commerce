package com.hhplus.ecommerce.infrastructure.persistence.coupon;

import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

/**
 * UserCoupon JPA Repository
 * Spring Data JPA를 통한 UserCoupon 엔티티 영구 저장소
 */
public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {
    Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId);

    /**
     * 비관적 락(Pessimistic Lock)을 사용하여 사용자 쿠폰 조회
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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uc FROM UserCoupon uc WHERE uc.userId = :userId AND uc.couponId = :couponId")
    Optional<UserCoupon> findByUserIdAndCouponIdForUpdate(@Param("userId") Long userId, @Param("couponId") Long couponId);

    @Query("SELECT uc FROM UserCoupon uc WHERE uc.userId = :userId AND uc.status = :status")
    List<UserCoupon> findByUserIdAndStatusString(@Param("userId") Long userId, @Param("status") UserCouponStatus status);

    List<UserCoupon> findByUserId(Long userId);

    /**
     * 사용자 쿠폰 삭제 (보상용)
     *
     * Outbox 패턴의 보상 로직에서 호출
     * - COUPON_ISSUE 이벤트의 보상 시 발급된 쿠폰 기록 삭제
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     */
    void deleteByUserIdAndCouponId(Long userId, Long couponId);
}
