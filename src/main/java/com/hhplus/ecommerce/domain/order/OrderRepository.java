package com.hhplus.ecommerce.domain.order;

import java.util.List;
import java.util.Optional;

/**
 * OrderRepository - Order 도메인 영속성 Port Interface
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
}
