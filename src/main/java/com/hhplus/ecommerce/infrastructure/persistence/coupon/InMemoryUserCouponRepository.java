package com.hhplus.ecommerce.infrastructure.persistence.coupon;

import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * InMemoryUserCouponRepository - Infrastructure 계층 (Adapter)
 *
 * 역할:
 * - ConcurrentHashMap 기반 사용자 쿠폰 발급 기록 저장소
 * - UNIQUE(user_id, coupon_id) 제약 조건 시뮬레이션
 * - 사용자별 쿠폰 상태 조회
 *
 * 특징:
 * - 스레드 안전성: ConcurrentHashMap 사용
 * - userCouponId 자동 증가 (2001L부터 시작)
 * - UNIQUE 검증을 통한 중복 발급 방지
 *
 * 마이그레이션:
 * - MySQL: user_coupons 테이블 → @Entity로 변환
 * - JPA: ConcurrentHashMap → JpaRepository
 * - 비즈니스 로직은 변경 없음
 *
 * 참고: Domain 계층의 UserCouponRepository는 인터페이스 (Port)
 * 이 클래스는 그 인터페이스의 구현체 (Adapter)
 */
@Repository
public class InMemoryUserCouponRepository implements UserCouponRepository {

    private final ConcurrentHashMap<Long, UserCoupon> userCouponStore = new ConcurrentHashMap<>();
    private Long userCouponIdSequence = 2001L;  // userCouponId 시작값

    /**
     * 사용자 쿠폰 발급 기록 저장
     *
     * @param userCoupon 저장할 사용자 쿠폰
     * @return 저장된 사용자 쿠폰 (userCouponId 포함)
     */
    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        Long userCouponId;
        synchronized (this) {
            userCouponId = userCouponIdSequence++;
        }
        userCoupon.setUserCouponId(userCouponId);
        userCouponStore.put(userCouponId, userCoupon);
        return userCoupon;
    }

    /**
     * 사용자 쿠폰 ID로 조회
     *
     * @param userCouponId 사용자 쿠폰 ID
     * @return 조회된 사용자 쿠폰 (없으면 Optional.empty())
     */
    @Override
    public Optional<UserCoupon> findById(Long userCouponId) {
        return Optional.ofNullable(userCouponStore.get(userCouponId));
    }

    /**
     * 사용자별 쿠폰 조회 (status별 필터링)
     *
     * @param userId 사용자 ID
     * @param status 쿠폰 상태 (ACTIVE | USED | EXPIRED)
     * @return 해당 상태의 사용자 쿠폰 목록
     */
    @Override
    public List<UserCoupon> findByUserIdAndStatus(Long userId, String status) {
        return userCouponStore.values().stream()
                .filter(uc -> uc.getUserId().equals(userId))
                .filter(uc -> uc.getStatus().equals(status))
                .collect(Collectors.toList());
    }

    /**
     * 사용자가 특정 쿠폰을 이미 발급받았는지 확인
     * UNIQUE(user_id, coupon_id) 검증 용도
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급 기록 (있으면 Optional 포함, 없으면 Optional.empty())
     */
    @Override
    public Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId) {
        return userCouponStore.values().stream()
                .filter(uc -> uc.getUserId().equals(userId) && uc.getCouponId().equals(couponId))
                .findFirst();
    }

    /**
     * 사용자의 모든 쿠폰 조회
     *
     * @param userId 사용자 ID
     * @return 사용자의 모든 쿠폰 목록
     */
    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        return userCouponStore.values().stream()
                .filter(uc -> uc.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    /**
     * 사용자 쿠폰 업데이트
     *
     * @param userCoupon 업데이트할 사용자 쿠폰
     * @return 업데이트된 사용자 쿠폰
     */
    @Override
    public UserCoupon update(UserCoupon userCoupon) {
        userCouponStore.put(userCoupon.getUserCouponId(), userCoupon);
        return userCoupon;
    }
}
