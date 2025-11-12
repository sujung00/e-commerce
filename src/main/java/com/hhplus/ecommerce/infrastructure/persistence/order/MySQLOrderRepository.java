package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import org.springframework.context.annotation.Primary;
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
@Transactional
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
        return orderJpaRepository.findById(orderId);
    }

    @Override
    public List<Order> findByUserId(Long userId, int page, int size) {
        // page와 size를 사용하여 offset 계산 (0-based)
        int offset = page * size;
        return orderJpaRepository.findByUserIdWithPagination(userId, offset, size);
    }

    @Override
    public long countByUserId(Long userId) {
        return orderJpaRepository.countByUserId(userId);
    }

    @Override
    public List<Order> findAll() {
        return orderJpaRepository.findAll();
    }
}
