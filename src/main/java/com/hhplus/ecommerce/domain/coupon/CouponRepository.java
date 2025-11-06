package com.hhplus.ecommerce.domain.coupon;

import java.util.List;
import java.util.Optional;

/**
 * CouponRepository - Domain 계층 (Port)
 *
 * 역할:
 * - 쿠폰 영속성 추상화
 * - Infrastructure 계층이 구현해야 할 인터페이스
 *
 * 참고: Port-Adapter 패턴
 * - 이 인터페이스는 Port (Domain이 정의)
 * - Infrastructure의 InMemoryCouponRepository가 Adapter (구현)
 */
public interface CouponRepository {
    /**
     * 쿠폰 저장 (발급 시 remaining_qty 감소)
     */
    Coupon save(Coupon coupon);

    /**
     * 쿠폰 ID로 조회
     */
    Optional<Coupon> findById(Long couponId);

    /**
     * 쿠폰을 비관적 락으로 조회 (SELECT ... FOR UPDATE)
     * 선착순 발급 시 다른 요청을 차단하기 위해 사용
     *
     * InMemory 구현:
     * - synchronized 블록으로 시뮬레이션
     * - 다른 요청이 같은 쿠폰을 잠글 때까지 대기
     *
     * MySQL+JPA 구현:
     * - @Lock(LockModeType.PESSIMISTIC_WRITE)
     * - SELECT ... FOR UPDATE
     */
    Optional<Coupon> findByIdForUpdate(Long couponId);

    /**
     * 모든 발급 가능한 쿠폰 조회
     * (is_active=true, valid period 내, remaining_qty > 0)
     */
    List<Coupon> findAllAvailable();

    /**
     * 모든 쿠폰 조회 (테스트/관리 용도)
     */
    List<Coupon> findAll();

    /**
     * 쿠폰 업데이트
     */
    Coupon update(Coupon coupon);
}
