package com.hhplus.ecommerce.infrastructure.persistence.coupon;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MySQL 기반 Coupon Repository 구현
 * Spring Data JPA를 사용한 영구 저장소
 *
 * Port(CouponRepository) 인터페이스를 구현하면서 JpaRepository 기능 제공
 */
@Repository
@Primary
@Transactional
public class MySQLCouponRepository implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    public MySQLCouponRepository(CouponJpaRepository couponJpaRepository) {
        this.couponJpaRepository = couponJpaRepository;
    }

    @Override
    public Coupon save(Coupon coupon) {
        return couponJpaRepository.save(coupon);
    }

    @Override
    public Optional<Coupon> findById(Long couponId) {
        return couponJpaRepository.findById(couponId);
    }

    @Override
    public Optional<Coupon> findByIdForUpdate(Long couponId) {
        // MySQL에서는 @Lock(LockModeType.PESSIMISTIC_WRITE)를 사용하여 구현
        return couponJpaRepository.findByIdWithLock(couponId);
    }

    @Override
    public List<Coupon> findAllAvailable() {
        LocalDateTime now = LocalDateTime.now();
        return couponJpaRepository.findAvailable(now);
    }

    @Override
    public List<Coupon> findAll() {
        return couponJpaRepository.findAll();
    }

    @Override
    public Coupon update(Coupon coupon) {
        return couponJpaRepository.save(coupon);
    }
}
