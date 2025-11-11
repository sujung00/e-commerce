package com.hhplus.ecommerce.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Order 도메인 엔티티 단위 테스트
 * - 주문 생성 팩토리 메서드
 * - 주문 항목 추가 기능
 * - 주문 상태 관리
 */
@DisplayName("Order 도메인 엔티티 테스트")
class OrderTest {

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_COUPON_ID = 100L;
    private static final Long TEST_SUBTOTAL = 100000L;
    private static final Long TEST_COUPON_DISCOUNT = 5000L;
    private static final Long TEST_FINAL_AMOUNT = 95000L;

    // ========== Order 생성 팩토리 메서드 ==========

    @Test
    @DisplayName("Order 생성 - 성공 (쿠폰 미적용)")
    void testCreateOrder_Success_NoCoupon() {
        // When
        Order order = Order.createOrder(TEST_USER_ID, null, 0L, TEST_SUBTOTAL, TEST_SUBTOTAL);

        // Then
        assertNotNull(order);
        assertEquals(TEST_USER_ID, order.getUserId());
        assertNull(order.getCouponId());
        assertEquals(0L, order.getCouponDiscount());
        assertEquals(TEST_SUBTOTAL, order.getSubtotal());
        assertEquals(TEST_SUBTOTAL, order.getFinalAmount());
        assertEquals("COMPLETED", order.getOrderStatus());
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
        assertNotNull(order.getOrderItems());
        assertTrue(order.getOrderItems().isEmpty());
    }

    @Test
    @DisplayName("Order 생성 - 성공 (쿠폰 적용)")
    void testCreateOrder_Success_WithCoupon() {
        // When
        Order order = Order.createOrder(
                TEST_USER_ID,
                TEST_COUPON_ID,
                TEST_COUPON_DISCOUNT,
                TEST_SUBTOTAL,
                TEST_FINAL_AMOUNT
        );

        // Then
        assertNotNull(order);
        assertEquals(TEST_USER_ID, order.getUserId());
        assertEquals(TEST_COUPON_ID, order.getCouponId());
        assertEquals(TEST_COUPON_DISCOUNT, order.getCouponDiscount());
        assertEquals(TEST_SUBTOTAL, order.getSubtotal());
        assertEquals(TEST_FINAL_AMOUNT, order.getFinalAmount());
        assertEquals("COMPLETED", order.getOrderStatus());
        assertTrue(order.getFinalAmount() < order.getSubtotal());
    }

    @Test
    @DisplayName("Order 생성 - 금액 계산 검증")
    void testCreateOrder_AmountValidation() {
        // When
        Order order = Order.createOrder(
                TEST_USER_ID,
                TEST_COUPON_ID,
                TEST_COUPON_DISCOUNT,
                TEST_SUBTOTAL,
                TEST_FINAL_AMOUNT
        );

        // Then
        // 최종 금액 = 소계 - 쿠폰 할인
        assertEquals(
                TEST_SUBTOTAL - TEST_COUPON_DISCOUNT,
                order.getFinalAmount()
        );
    }

    @Test
    @DisplayName("Order 생성 - 상태는 항상 COMPLETED")
    void testCreateOrder_StatusAlwaysCompleted() {
        // When
        Order order1 = Order.createOrder(TEST_USER_ID, null, 0L, 50000L, 50000L);
        Order order2 = Order.createOrder(TEST_USER_ID, 1L, 5000L, 50000L, 45000L);

        // Then
        assertEquals("COMPLETED", order1.getOrderStatus());
        assertEquals("COMPLETED", order2.getOrderStatus());
    }

    // ========== 주문 항목 추가 ==========

    @Test
    @DisplayName("주문 항목 추가 - 성공 (단일 항목)")
    void testAddOrderItem_Success_SingleItem() {
        // Given
        Order order = Order.createOrder(TEST_USER_ID, null, 0L, TEST_SUBTOTAL, TEST_SUBTOTAL);
        OrderItem orderItem = OrderItem.builder()
                .orderItemId(1L)
                .productId(100L)
                .optionId(1L)
                .productName("상품1")
                .optionName("옵션1")
                .quantity(2)
                .unitPrice(50000L)
                .subtotal(100000L)
                .createdAt(LocalDateTime.now())
                .build();

        // When
        order.addOrderItem(orderItem);

        // Then
        assertEquals(1, order.getOrderItems().size());
        assertEquals(orderItem, order.getOrderItems().get(0));
        assertEquals(100L, order.getOrderItems().get(0).getProductId());
    }

    @Test
    @DisplayName("주문 항목 추가 - 성공 (다중 항목)")
    void testAddOrderItem_Success_MultipleItems() {
        // Given
        Order order = Order.createOrder(TEST_USER_ID, null, 0L, TEST_SUBTOTAL, TEST_SUBTOTAL);
        OrderItem item1 = OrderItem.builder()
                .orderItemId(1L)
                .productId(100L)
                .optionId(1L)
                .productName("상품1")
                .optionName("옵션1")
                .quantity(1)
                .unitPrice(50000L)
                .subtotal(50000L)
                .createdAt(LocalDateTime.now())
                .build();
        OrderItem item2 = OrderItem.builder()
                .orderItemId(2L)
                .productId(200L)
                .optionId(2L)
                .productName("상품2")
                .optionName("옵션2")
                .quantity(1)
                .unitPrice(50000L)
                .subtotal(50000L)
                .createdAt(LocalDateTime.now())
                .build();

        // When
        order.addOrderItem(item1);
        order.addOrderItem(item2);

        // Then
        assertEquals(2, order.getOrderItems().size());
        assertEquals(100L, order.getOrderItems().get(0).getProductId());
        assertEquals(200L, order.getOrderItems().get(1).getProductId());
    }

    @Test
    @DisplayName("주문 항목 추가 - 순서 유지")
    void testAddOrderItem_OrderPreservation() {
        // Given
        Order order = Order.createOrder(TEST_USER_ID, null, 0L, TEST_SUBTOTAL, TEST_SUBTOTAL);
        OrderItem item1 = OrderItem.createOrderItem(100L, 1L, "상품1", "옵션1", 1, 50000L);
        OrderItem item2 = OrderItem.createOrderItem(200L, 2L, "상품2", "옵션2", 1, 50000L);
        OrderItem item3 = OrderItem.createOrderItem(300L, 3L, "상품3", "옵션3", 1, 50000L);

        // When
        order.addOrderItem(item1);
        order.addOrderItem(item2);
        order.addOrderItem(item3);

        // Then
        assertEquals(3, order.getOrderItems().size());
        assertEquals(100L, order.getOrderItems().get(0).getProductId());
        assertEquals(200L, order.getOrderItems().get(1).getProductId());
        assertEquals(300L, order.getOrderItems().get(2).getProductId());
    }

    // ========== Order 빌더 패턴 ==========

    @Test
    @DisplayName("Order 빌더 - 모든 필드 설정 가능")
    void testOrderBuilder_AllFields() {
        // When
        Order order = Order.builder()
                .orderId(1L)
                .userId(TEST_USER_ID)
                .orderStatus("PENDING")
                .couponId(TEST_COUPON_ID)
                .couponDiscount(TEST_COUPON_DISCOUNT)
                .subtotal(TEST_SUBTOTAL)
                .finalAmount(TEST_FINAL_AMOUNT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Then
        assertEquals(1L, order.getOrderId());
        assertEquals(TEST_USER_ID, order.getUserId());
        assertEquals("PENDING", order.getOrderStatus());
        assertEquals(TEST_COUPON_ID, order.getCouponId());
    }

    @Test
    @DisplayName("Order 빌더 - 기본 orderItems는 빈 리스트")
    void testOrderBuilder_DefaultEmptyOrderItems() {
        // When
        Order order = Order.builder()
                .orderId(1L)
                .userId(TEST_USER_ID)
                .build();

        // Then
        assertNotNull(order.getOrderItems());
        assertTrue(order.getOrderItems().isEmpty());
    }

    // ========== Order 속성 변경 ==========

    @Test
    @DisplayName("주문 상태 변경 - Setter를 통한 상태 변경")
    void testOrderStatusChange() {
        // Given
        Order order = Order.createOrder(TEST_USER_ID, null, 0L, TEST_SUBTOTAL, TEST_SUBTOTAL);
        assertEquals("COMPLETED", order.getOrderStatus());

        // When
        order.setOrderStatus("CANCELLED");

        // Then
        assertEquals("CANCELLED", order.getOrderStatus());
    }

    @Test
    @DisplayName("주문 타임스탬프 - 생성 시 자동 설정")
    void testOrderTimestamps() {
        // When
        LocalDateTime beforeCreation = LocalDateTime.now();
        Order order = Order.createOrder(TEST_USER_ID, null, 0L, TEST_SUBTOTAL, TEST_SUBTOTAL);
        LocalDateTime afterCreation = LocalDateTime.now();

        // Then
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
        assertFalse(order.getCreatedAt().isBefore(beforeCreation));
        assertFalse(order.getCreatedAt().isAfter(afterCreation.plusSeconds(1)));
    }

    // ========== 경계값 테스트 ==========

    @Test
    @DisplayName("Order 생성 - 최소 금액 (0원)")
    void testCreateOrder_ZeroAmount() {
        // When
        Order order = Order.createOrder(TEST_USER_ID, null, 0L, 0L, 0L);

        // Then
        assertEquals(0L, order.getSubtotal());
        assertEquals(0L, order.getFinalAmount());
    }

    @Test
    @DisplayName("Order 생성 - 대금액")
    void testCreateOrder_LargeAmount() {
        // When
        Long largeAmount = 999999999L;
        Order order = Order.createOrder(TEST_USER_ID, null, 0L, largeAmount, largeAmount);

        // Then
        assertEquals(largeAmount, order.getSubtotal());
        assertEquals(largeAmount, order.getFinalAmount());
    }

    @Test
    @DisplayName("Order 생성 - 쿠폰 할인이 소계보다 큰 경우")
    void testCreateOrder_DiscountGreaterThanSubtotal() {
        // When: 할인이 소계보다 크면 음수 금액이 될 수 있음
        Long subtotal = 50000L;
        Long discount = 60000L;
        Order order = Order.createOrder(
                TEST_USER_ID,
                TEST_COUPON_ID,
                discount,
                subtotal,
                subtotal - discount  // 음수
        );

        // Then: 도메인에서는 검증하지 않음 (Application 계층의 책임)
        assertEquals(subtotal - discount, order.getFinalAmount());
    }

    @Test
    @DisplayName("주문 항목 추가 - 많은 항목 추가")
    void testAddOrderItem_ManyItems() {
        // Given
        Order order = Order.createOrder(TEST_USER_ID, null, 0L, 1000000L, 1000000L);

        // When
        for (int i = 1; i <= 100; i++) {
            OrderItem item = OrderItem.createOrderItem(
                    (long) i,
                    (long) i,
                    "상품" + i,
                    "옵션" + i,
                    1,
                    10000L
            );
            order.addOrderItem(item);
        }

        // Then
        assertEquals(100, order.getOrderItems().size());
    }

    @Test
    @DisplayName("Order 필드 null 안전성")
    void testOrderNullSafety() {
        // When
        Order order = Order.builder().build();

        // Then
        assertNull(order.getOrderId());
        assertNull(order.getUserId());
        assertNull(order.getOrderStatus());
        assertNotNull(order.getOrderItems());  // Builder.Default 설정
    }
}
