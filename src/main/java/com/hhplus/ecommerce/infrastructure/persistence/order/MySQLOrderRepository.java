package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * MySQL 기반 Order Repository 구현
 * Spring Data JPA를 사용한 영구 저장소
 *
 * Port(OrderRepository) 인터페이스를 구현하면서 JpaRepository 기능 제공
 */
@Repository
@Primary
public class MySQLOrderRepository implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    public MySQLOrderRepository(OrderJpaRepository orderJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
    }

    @Override
    public Order save(Order order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        // ✅ FetchType.LAZY: orderItems를 함께 로드하기 위해 fetch join 사용
        return orderJpaRepository.findByIdWithItems(orderId);
    }

    @Override
    @Transactional
    public Optional<Order> findByIdForUpdate(Long orderId) {
        // ✅ 비관적 락 적용: SELECT ... FOR UPDATE로 배타적 잠금
        // - @Lock(LockModeType.PESSIMISTIC_WRITE)이 DB 레벨 잠금 처리
        // - @Transactional으로 트랜잭션 내에서만 잠금 유효 (commit 또는 rollback 시 해제)
        // - orderItems도 함께 로드 (fetch join)
        return orderJpaRepository.findByIdForUpdate(orderId);
    }

    @Override
    public List<Order> findByUserId(Long userId, int page, int size) {
        // page와 size를 사용하여 Pageable 객체 생성 (0-based)
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return orderJpaRepository.findByUserIdWithPagination(userId, pageable);
    }

    @Override
    public long countByUserId(Long userId) {
        return orderJpaRepository.countByUserId(userId);
    }

    @Override
    public List<Order> findAll() {
        return orderJpaRepository.findAll();
    }

    @Override
    public boolean isCouponUsed(Long userId, Long couponId) {
        return orderJpaRepository.existsByUserIdAndCouponIdAndStatus(userId, couponId);
    }

    @Override
    public boolean existsOrderWithCoupon(Long userId, Long couponId) {
        return orderJpaRepository.existsByUserIdAndCouponIdAndStatus(userId, couponId);
    }

    @Override
    public boolean existsActiveByCouponId(Long couponId) {
        return orderJpaRepository.existsActiveByCouponId(couponId);
    }
}
