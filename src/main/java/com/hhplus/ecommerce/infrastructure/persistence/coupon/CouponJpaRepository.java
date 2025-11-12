package com.hhplus.ecommerce.infrastructure.persistence.coupon;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Coupon JPA Repository
 * Spring Data JPA를 통한 Coupon 엔티티 영구 저장소
 */
public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {

    /**
     * 쿠폰 ID로 조회 (비관적 락 - 동시성 제어)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.couponId = :couponId")
    Optional<Coupon> findByIdWithLock(@Param("couponId") Long couponId);

    /**
     * 현재 시점에서 유효한 활성 쿠폰 조회
     */
    @Query("SELECT c FROM Coupon c WHERE c.isActive = true AND c.validFrom <= :now AND c.validUntil >= :now")
    List<Coupon> findAvailable(@Param("now") LocalDateTime now);
}
