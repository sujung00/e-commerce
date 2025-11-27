package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Order JPA Repository
 * Spring Data JPA를 통한 Order 엔티티 영구 저장소
 *
 * ✅ FetchType 정책 (2025-11-18):
 * - Order.orderItems: FetchType.LAZY로 변경
 * - 필요한 조회 시 fetch join 사용
 * - N+1 문제 방지를 위해 명시적 fetch join 적용
 */
public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    /**
     * 사용자별 주문 조회 (페이지네이션)
     * ✅ fetch join으로 orderItems 함께 로드
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.orderItems oi " +
           "WHERE o.userId = :userId " +
           "ORDER BY o.createdAt DESC")
    List<Order> findByUserIdWithPagination(@Param("userId") Long userId, Pageable pageable);

    /**
     * 주문 ID로 조회 (orderItems 함께 로드)
     * ✅ fetch join으로 orderItems 함께 로드
     */
    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.orderItems oi " +
           "WHERE o.orderId = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

    /**
     * 주문 ID로 조회 (비관적 락 - SELECT ... FOR UPDATE)
     *
     * 동시성 제어:
     * - DB 레벨 배타적 잠금 (Pessimistic Lock)
     * - 다른 트랜잭션의 읽기/쓰기 모두 차단
     * - LockModeType.PESSIMISTIC_WRITE: UPDATE/DELETE 차단
     *
     * SQL 생성:
     * SELECT ... FROM orders o LEFT JOIN fetch o.orderItems oi WHERE o.order_id=? FOR UPDATE
     *
     * 사용 예:
     * - updateOrderAsPaid(): PENDING → PAID 전환 전
     * - compensateOrder(): PENDING → FAILED → CANCELLED 전환 전
     *
     * @param orderId 주문 ID
     * @return 배타적 잠금이 적용된 Order (orderItems 함께 로드)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.orderItems oi " +
           "WHERE o.orderId = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    long countByUserId(Long userId);

    /**
     * 쿠폰 사용 여부 확인
     *
     * 변경 사항 (2025-11-18):
     * - USER_COUPONS.order_id 삭제로 인한 새로운 쿠폰 사용 추적
     * - orders.coupon_id로만 쿠폰 사용을 추적
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return true: 해당 사용자가 해당 쿠폰을 사용 중인 활성 주문이 있음
     */
    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END " +
           "FROM Order o " +
           "WHERE o.userId = :userId AND o.couponId = :couponId")
    boolean existsByUserIdAndCouponIdAndStatus(@Param("userId") Long userId, @Param("couponId") Long couponId);

    /**
     * 쿠폰이 현재 활성 주문(미취소 상태)에서 사용 중인지 확인
     *
     * 주문 취소 시 쿠폰 복구 여부를 결정하기 위해 사용
     * - 해당 쿠폰이 다른 활성 주문(COMPLETED)에서 사용 중이면 복구하지 않음
     * - 사용 중이 아니면 UserCoupon.status를 UNUSED로 복구
     *
     * 변경 사항 (2025-11-18):
     * - 쿠폰 사용 여부는 orders.coupon_id 존재 여부로만 판단
     *
     * @param couponId 쿠폰 ID
     * @return true: 활성 주문에서 사용 중, false: 미사용 상태
     */
    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END " +
           "FROM Order o " +
           "WHERE o.couponId = :couponId AND o.orderStatus = 'COMPLETED'")
    boolean existsActiveByCouponId(@Param("couponId") Long couponId);
}
