package com.hhplus.ecommerce.infrastructure.persistence.order;

import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryOrderRepository 테스트")
class InMemoryOrderRepositoryTest {

    private InMemoryOrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
    }

    @Test
    @DisplayName("save - 새 주문 저장 (ID 자동 할당)")
    void testSave_NewOrder() {
        // When
        Order order = Order.createOrder(100L, 1L, 5000L, 100000L, 95000L);
        Order saved = orderRepository.save(order);

        // Then
        assertNotNull(saved.getOrderId());
        assertTrue(saved.getOrderId() > 0);
    }

    @Test
    @DisplayName("save - 주문 아이템 ID 자동 할당")
    void testSave_OrderItemIdAssignment() {
        // When
        Order order = Order.createOrder(100L, null, 0L, 100000L, 100000L);
        OrderItem item = OrderItem.createOrderItem(1L, 1L, "상품", "옵션", 1, 100000L);
        order.addOrderItem(item);

        Order saved = orderRepository.save(order);

        // Then
        assertNotNull(saved.getOrderItems().get(0).getOrderItemId());
        assertEquals(saved.getOrderId(), saved.getOrderItems().get(0).getOrderId());
    }

    @Test
    @DisplayName("findById - 저장된 주문 조회")
    void testFindById_ExistingOrder() {
        // Given
        Order order = Order.createOrder(100L, null, 0L, 50000L, 50000L);
        Order saved = orderRepository.save(order);

        // When
        Optional<Order> found = orderRepository.findById(saved.getOrderId());

        // Then
        assertTrue(found.isPresent());
        assertEquals(100L, found.get().getUserId());
    }

    @Test
    @DisplayName("findById - 없는 주문은 Optional.empty()")
    void testFindById_NonExistent() {
        // When
        Optional<Order> found = orderRepository.findById(99999L);

        // Then
        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("findByUserId - 사용자의 주문 목록 조회")
    void testFindByUserId_UserOrders() {
        // Given
        Order order1 = Order.createOrder(200L, null, 0L, 100000L, 100000L);
        Order order2 = Order.createOrder(200L, null, 0L, 50000L, 50000L);
        orderRepository.save(order1);
        orderRepository.save(order2);

        // When
        List<Order> orders = orderRepository.findByUserId(200L, 0, 10);

        // Then
        assertEquals(2, orders.size());
        assertTrue(orders.stream().allMatch(o -> 200L == o.getUserId()));
    }

    @Test
    @DisplayName("findByUserId - 페이징 처리")
    void testFindByUserId_Pagination() {
        // Given
        for (int i = 0; i < 5; i++) {
            Order order = Order.createOrder(300L, null, 0L, 10000L, 10000L);
            orderRepository.save(order);
        }

        // When
        List<Order> page1 = orderRepository.findByUserId(300L, 0, 2);
        List<Order> page2 = orderRepository.findByUserId(300L, 1, 2);

        // Then
        assertEquals(2, page1.size());
        assertEquals(2, page2.size());
    }

    @Test
    @DisplayName("countByUserId - 사용자의 주문 개수 조회")
    void testCountByUserId_CountOrders() {
        // Given
        for (int i = 0; i < 3; i++) {
            Order order = Order.createOrder(400L, null, 0L, 10000L, 10000L);
            orderRepository.save(order);
        }

        // When
        long count = orderRepository.countByUserId(400L);

        // Then
        assertEquals(3, count);
    }

    @Test
    @DisplayName("findAll - 모든 주문 조회")
    void testFindAll_AllOrders() {
        // Given
        Order order1 = Order.createOrder(500L, null, 0L, 100000L, 100000L);
        Order order2 = Order.createOrder(501L, null, 0L, 50000L, 50000L);
        orderRepository.save(order1);
        orderRepository.save(order2);

        // When
        List<Order> all = orderRepository.findAll();

        // Then
        assertTrue(all.size() >= 2);
    }

    @Test
    @DisplayName("주문 생성 시퀀스 - ID 증가")
    void testOrderIdSequence_Incrementing() {
        // When
        Order order1 = Order.createOrder(600L, null, 0L, 10000L, 10000L);
        Order saved1 = orderRepository.save(order1);

        Order order2 = Order.createOrder(601L, null, 0L, 10000L, 10000L);
        Order saved2 = orderRepository.save(order2);

        // Then
        assertTrue(saved1.getOrderId() < saved2.getOrderId());
    }

    @Test
    @DisplayName("clear - 모든 주문 삭제")
    void testClear_ClearAllOrders() {
        // When
        orderRepository.clear();
        List<Order> all = orderRepository.findAll();

        // Then
        assertTrue(all.isEmpty());
    }

    @Test
    @DisplayName("사용 시나리오 - 주문 생성 및 조회")
    void testScenario_CreateAndFindOrder() {
        // When
        Order order = Order.createOrder(700L, 1L, 5000L, 100000L, 95000L);
        OrderItem item = OrderItem.createOrderItem(10L, 1L, "상품명", "옵션명", 2, 50000L);
        order.addOrderItem(item);

        Order saved = orderRepository.save(order);
        Optional<Order> found = orderRepository.findById(saved.getOrderId());

        // Then
        assertTrue(found.isPresent());
        assertEquals(700L, found.get().getUserId());
        assertEquals(1, found.get().getOrderItems().size());
        assertEquals(95000L, found.get().getFinalAmount());
    }

    @Test
    @DisplayName("사용 시나리오 - 사용자 주문 히스토리 조회")
    void testScenario_UserOrderHistory() {
        // Given
        for (int i = 0; i < 3; i++) {
            Order order = Order.createOrder(800L, null, 0L, 100000L, 100000L);
            orderRepository.save(order);
        }

        // When
        List<Order> orders = orderRepository.findByUserId(800L, 0, 10);
        long count = orderRepository.countByUserId(800L);

        // Then
        assertEquals(count, orders.size());
        assertTrue(orders.stream().allMatch(o -> o.getCreatedAt() != null));
    }

    @Test
    @DisplayName("사용 시나리오 - 다중 사용자 주문")
    void testScenario_MultipleUsers() {
        // When
        Order order1 = Order.createOrder(900L, null, 0L, 100000L, 100000L);
        Order order2 = Order.createOrder(901L, null, 0L, 100000L, 100000L);
        orderRepository.save(order1);
        orderRepository.save(order2);

        // Then
        assertEquals(1, orderRepository.countByUserId(900L));
        assertEquals(1, orderRepository.countByUserId(901L));
    }
}
