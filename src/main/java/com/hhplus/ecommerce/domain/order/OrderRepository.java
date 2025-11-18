package com.hhplus.ecommerce.domain.order;

import java.util.List;
import java.util.Optional;

/**
 * InMemoryOrderRepository - Order 도메인 영속성 Port Interface
 * 주문 데이터의 저장 및 조회를 담당
 */
public interface OrderRepository {
    /**
     * 주문 저장
     */
    Order save(Order order);

    /**
     * 주문 ID로 조회
     */
    Optional<Order> findById(Long orderId);

    /**
     * 사용자별 주문 목록 조회 (페이지네이션)
     */
    List<Order> findByUserId(Long userId, int page, int size);

    /**
     * 사용자의 주문 총 개수 조회
     */
    long countByUserId(Long userId);

    /**
     * 모든 주문 조회
     */
    List<Order> findAll();

    /**
     * 쿠폰별 사용 여부 확인
     *
     * 변경 사항 (2025-11-18):
     * - USER_COUPONS.order_id 삭제로 인한 새로운 쿠폰 사용 추적 방식
     * - 쿠폰 사용 여부는 ORDERS.coupon_id의 존재 여부로 판단
     * - 주어진 userId & couponId 조합으로 활성 주문이 있는지 확인
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return true: 쿠폰이 사용 중인 활성 주문이 있음, false: 미사용 상태
     */
    boolean isCouponUsed(Long userId, Long couponId);

    /**
     * 주문 생성 전 쿠폰 소유 검증
     *
     * 주문 생성 시에는 다음을 확인해야 함:
     * 1. 사용자가 쿠폰을 보유하고 있는가? (user_coupons에서 확인)
     * 2. 쿠폰이 이미 사용 중인가? (orders.coupon_id에서 확인)
     *
     * 이 메서드는 orders 테이블에서만 쿠폰 사용 여부를 확인합니다.
     * UserCouponRepository와 함께 사용되어야 합니다.
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return true: 쿠폰이 다른 주문에서 사용 중, false: 사용 가능
     */
    boolean existsOrderWithCoupon(Long userId, Long couponId);

    /**
     * 쿠폰이 현재 활성 주문(미취소 상태)에서 사용 중인지 확인
     *
     * 변경 사항 (2025-11-18):
     * - USER_COUPONS.order_id 삭제로 인한 새로운 확인 방식
     * - 쿠폰 사용 여부는 ORDERS.coupon_id의 존재 여부로 판단
     * - 주문 취소 시 쿠폰 복구 여부 결정에 사용
     *
     * @param couponId 쿠폰 ID
     * @return true: 활성 주문에서 사용 중, false: 미사용 상태
     */
    boolean existsActiveByCouponId(Long couponId);
}
