package com.hhplus.ecommerce.infrastructure.persistence.coupon;

import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * MySQL 기반 UserCoupon Repository 구현
 * Spring Data JPA를 사용한 영구 저장소
 *
 * Port(UserCouponRepository) 인터페이스를 구현하면서 JpaRepository 기능 제공
 */
@Repository
@Primary
public class MySQLUserCouponRepository implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;

    public MySQLUserCouponRepository(UserCouponJpaRepository userCouponJpaRepository) {
        this.userCouponJpaRepository = userCouponJpaRepository;
    }

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        return userCouponJpaRepository.save(userCoupon);
    }

    @Override
    public Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId) {
        return userCouponJpaRepository.findByUserIdAndCouponId(userId, couponId);
    }

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
    public Optional<UserCoupon> findByUserIdAndCouponIdForUpdate(Long userId, Long couponId) {
        return userCouponJpaRepository.findByUserIdAndCouponIdForUpdate(userId, couponId);
    }

    @Override
    public List<UserCoupon> findByUserIdAndStatus(Long userId, String status) {
        // String status를 UserCouponStatus enum으로 변환
        UserCouponStatus couponStatus = UserCouponStatus.valueOf(status);
        return userCouponJpaRepository.findByUserIdAndStatusString(userId, couponStatus);
    }

    @Override
    public Optional<UserCoupon> findById(Long userCouponId) {
        return userCouponJpaRepository.findById(userCouponId);
    }

    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        return userCouponJpaRepository.findByUserId(userId);
    }

    @Override
    public UserCoupon update(UserCoupon userCoupon) {
        return userCouponJpaRepository.save(userCoupon);
    }

    @Override
    @Transactional
    public void deleteByUserIdAndCouponId(Long userId, Long couponId) {
        userCouponJpaRepository.deleteByUserIdAndCouponId(userId, couponId);
    }
}
