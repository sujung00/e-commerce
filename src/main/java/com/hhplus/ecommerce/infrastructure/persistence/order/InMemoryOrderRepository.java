package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.Order;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * InMemoryOrderRepository - Order 저장소 구현체 (인메모리)
 * ConcurrentHashMap을 사용하여 스레드 안전성 제공
 */
@Repository
public class InMemoryOrderRepository implements com.hhplus.ecommerce.domain.order.OrderRepository {

    private final ConcurrentHashMap<Long, Order> orders = new ConcurrentHashMap<>();
    private Long orderIdSequence = 5000L;  // 초기 order_id 값

    @Override
    public Order save(Order order) {
        // ID가 없으면 새로운 ID 할당
        if (order.getOrderId() == null) {
            synchronized (this) {
                order.setOrderId(++orderIdSequence);
                // OrderItem들에도 orderId와 orderItemId 할당
                long orderItemIdSequence = 5000L;
                for (var item : order.getOrderItems()) {
                    if (item.getOrderId() == null) {
                        item.setOrderId(order.getOrderId());
                    }
                    if (item.getOrderItemId() == null) {
                        item.setOrderItemId(++orderItemIdSequence);
                    }
                }
            }
        }
        orders.put(order.getOrderId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    @Override
    public List<Order> findByUserId(Long userId, int page, int size) {
        return orders.values().stream()
                .filter(order -> order.getUserId().equals(userId))
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }

    @Override
    public long countByUserId(Long userId) {
        return orders.values().stream()
                .filter(order -> order.getUserId().equals(userId))
                .count();
    }

    @Override
    public List<Order> findAll() {
        return new ArrayList<>(orders.values());
    }

    /**
     * 테스트용: 모든 주문 삭제
     */
    public void clear() {
        orders.clear();
    }
}
