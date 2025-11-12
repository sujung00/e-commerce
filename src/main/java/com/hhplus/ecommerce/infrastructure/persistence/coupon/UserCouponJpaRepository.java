package com.hhplus.ecommerce.infrastructure.persistence.coupon;

import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * UserCoupon JPA Repository
 * Spring Data JPA를 통한 UserCoupon 엔티티 영구 저장소
 */
public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {
    Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId);

    @Query("SELECT uc FROM UserCoupon uc WHERE uc.userId = :userId AND uc.status = CAST(:status AS com.hhplus.ecommerce.domain.coupon.UserCouponStatus)")
    List<UserCoupon> findByUserIdAndStatusString(@Param("userId") Long userId, @Param("status") String status);

    List<UserCoupon> findByUserId(Long userId);
}
