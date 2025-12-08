package com.hhplus.ecommerce.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Order 도메인 엔티티 순수 단위 테스트
 *
 * 테스트 대상:
 * - 주문 생성 팩토리 메서드 (비즈니스 규칙 검증)
 * - 주문 항목 추가 기능
 * - 주문 상태 관리 및 상태 전환 로직
 * - 주문 금액 계산 및 조회
 * - 주문 상태 확인 메서드들
 *
 * 특징: Mock 없이 실제 도메인 객체만 사용
 */
@DisplayName("Order 도메인 엔티티 순수 단위 테스트")
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
        assertEquals(OrderStatus.COMPLETED, order.getOrderStatus());
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
        assertEquals(OrderStatus.COMPLETED, order.getOrderStatus());
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
        assertEquals(OrderStatus.COMPLETED, order1.getOrderStatus());
        assertEquals(OrderStatus.COMPLETED, order2.getOrderStatus());
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
                .orderStatus(OrderStatus.PENDING)
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
        assertEquals(OrderStatus.PENDING, order.getOrderStatus());
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
    @DisplayName("주문 상태 변경 - Builder로 상태 변경된 객체 생성")
    void testOrderStatusChange() {
        // Given
        Order order = Order.createOrder(TEST_USER_ID, null, 0L, TEST_SUBTOTAL, TEST_SUBTOTAL);
        assertEquals(OrderStatus.COMPLETED, order.getOrderStatus());

        // When - 상태를 변경하려면 새 객체를 Builder로 생성
        Order updatedOrder = Order.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .orderStatus(OrderStatus.CANCELLED)
                .couponId(order.getCouponId())
                .couponDiscount(order.getCouponDiscount())
                .subtotal(order.getSubtotal())
                .finalAmount(order.getFinalAmount())
                .createdAt(order.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        // Then
        assertEquals(OrderStatus.CANCELLED, updatedOrder.getOrderStatus());
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
        // 음수 금액은 유효하지 않으므로 예외 발생 확인
        assertThrows(IllegalArgumentException.class, () ->
            Order.createOrder(
                    TEST_USER_ID,
                    TEST_COUPON_ID,
                    discount,
                    subtotal,
                    subtotal - discount  // 음수
            )
        );
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
        assertNull(order.getOrderStatus());  // 명시적으로 설정되지 않으면 null
        assertNotNull(order.getOrderItems());  // Builder.Default 설정
    }

    // ========== 새로운 비즈니스 로직: 주문 취소 ==========

    @Nested
    @DisplayName("주문 취소 로직")
    class OrderCancellationTests {

        @Test
        @DisplayName("주문 취소 - 성공 (COMPLETED 상태)")
        void testCancel_Success() {
            // Given
            Order order = Order.createOrder(TEST_USER_ID, TEST_COUPON_ID, TEST_COUPON_DISCOUNT,
                                           TEST_SUBTOTAL, TEST_FINAL_AMOUNT);
            assertTrue(order.isCancellable());

            // When
            order.cancel();

            // Then
            assertTrue(order.isCancelled());
            assertFalse(order.isCancellable());
            assertNotNull(order.getCancelledAt());
            assertTrue(order.getCancelledAt().isBefore(LocalDateTime.now().plusSeconds(1)));
        }

        @Test
        @DisplayName("주문 취소 - 실패 (이미 취소된 주문)")
        void testCancel_AlreadyCancelled() {
            // Given
            Order order = Order.createOrder(TEST_USER_ID, null, 0L, TEST_SUBTOTAL, TEST_SUBTOTAL);
            order.cancel();  // 첫 취소
            assertTrue(order.isCancelled());

            // When & Then
            assertThrows(InvalidOrderStatusException.class, order::cancel);
        }

        @Test
        @DisplayName("주문 취소 - 취소 시 상태와 타임스탬프 업데이트")
        void testCancel_UpdatesTimestamp() {
            // Given
            Order order = Order.createOrder(TEST_USER_ID, null, 0L, TEST_SUBTOTAL, TEST_SUBTOTAL);
            LocalDateTime beforeCancel = order.getUpdatedAt();

            // When
            order.cancel();

            // Then
            assertTrue(order.getUpdatedAt().isAfter(beforeCancel));
            assertNotNull(order.getCancelledAt());
        }
    }

    // ========== 새로운 비즈니스 로직: 주문 상태 확인 ==========

    @Nested
    @DisplayName("주문 상태 확인 메서드")
    class OrderStatusCheckTests {

        @Test
        @DisplayName("isCancellable - COMPLETED 상태만 취소 가능")
        void testIsCancellable() {
            // Given
            Order completedOrder = Order.createOrder(TEST_USER_ID, null, 0L, 50000L, 50000L);

            // When & Then
            assertTrue(completedOrder.isCancellable());
        }

        @Test
        @DisplayName("isCompleted - COMPLETED 상태 확인")
        void testIsCompleted() {
            // Given
            Order order = Order.createOrder(TEST_USER_ID, null, 0L, 50000L, 50000L);

            // When & Then
            assertTrue(order.isCompleted());
            assertFalse(order.isCancelled());
        }

        @Test
        @DisplayName("hasCoupon - 쿠폰 적용 여부 확인")
        void testHasCoupon() {
            // Given
            Order withCoupon = Order.createOrder(TEST_USER_ID, TEST_COUPON_ID, TEST_COUPON_DISCOUNT,
                                                 TEST_SUBTOTAL, TEST_FINAL_AMOUNT);
            Order noCoupon = Order.createOrder(TEST_USER_ID, null, 0L, TEST_SUBTOTAL, TEST_SUBTOTAL);

            // When & Then
            assertTrue(withCoupon.hasCoupon());
            assertFalse(noCoupon.hasCoupon());
        }
    }

    // ========== 새로운 비즈니스 로직: 금액 계산 및 조회 ==========

    @Nested
    @DisplayName("금액 계산 및 조회")
    class OrderAmountCalculationTests {

        @Test
        @DisplayName("getPaymentAmount - 실제 결제액 조회")
        void testGetPaymentAmount() {
            // Given
            Order order = Order.createOrder(TEST_USER_ID, TEST_COUPON_ID, TEST_COUPON_DISCOUNT,
                                           TEST_SUBTOTAL, TEST_FINAL_AMOUNT);

            // When
            Long paymentAmount = order.getPaymentAmount();

            // Then
            assertEquals(TEST_FINAL_AMOUNT, paymentAmount);
            assertEquals(95000L, paymentAmount);
        }

        @Test
        @DisplayName("getBaseAmount - 쿠폰 제외 원가 합계 조회")
        void testGetBaseAmount() {
            // Given
            Order order = Order.createOrder(TEST_USER_ID, TEST_COUPON_ID, TEST_COUPON_DISCOUNT,
                                           TEST_SUBTOTAL, TEST_FINAL_AMOUNT);

            // When
            Long baseAmount = order.getBaseAmount();

            // Then
            assertEquals(TEST_SUBTOTAL, baseAmount);
            assertEquals(100000L, baseAmount);
        }

        @Test
        @DisplayName("getTotalDiscount - 총 할인액 조회")
        void testGetTotalDiscount() {
            // Given
            Order order = Order.createOrder(TEST_USER_ID, TEST_COUPON_ID, TEST_COUPON_DISCOUNT,
                                           TEST_SUBTOTAL, TEST_FINAL_AMOUNT);

            // When
            Long discount = order.getTotalDiscount();

            // Then
            assertEquals(TEST_COUPON_DISCOUNT, discount);
            assertEquals(5000L, discount);
        }

        @Test
        @DisplayName("getDiscountPercentage - 할인율 계산")
        void testGetDiscountPercentage() {
            // Given
            Order order = Order.createOrder(TEST_USER_ID, TEST_COUPON_ID, 50000L,
                                           100000L, 50000L);

            // When
            double discountPercentage = order.getDiscountPercentage();

            // Then
            assertEquals(50.0, discountPercentage);
        }

        @Test
        @DisplayName("getDiscountPercentage - 할인 없는 경우 0%")
        void testGetDiscountPercentage_NoDiscount() {
            // Given
            Order order = Order.createOrder(TEST_USER_ID, null, 0L, TEST_SUBTOTAL, TEST_SUBTOTAL);

            // When
            double discountPercentage = order.getDiscountPercentage();

            // Then
            assertEquals(0.0, discountPercentage);
        }

        @Test
        @DisplayName("getDiscountPercentage - 소계가 0인 경우 0%")
        void testGetDiscountPercentage_ZeroSubtotal() {
            // Given
            Order order = Order.createOrder(TEST_USER_ID, null, 0L, 0L, 0L);

            // When
            double discountPercentage = order.getDiscountPercentage();

            // Then
            assertEquals(0.0, discountPercentage);
        }
    }

    // ========== 새로운 비즈니스 로직: 주문 항목 관리 ==========

    @Nested
    @DisplayName("주문 항목 관리")
    class OrderItemManagementTests {

        @Test
        @DisplayName("getOrderItemCount - 주문 항목 개수 조회")
        void testGetOrderItemCount() {
            // Given
            Order order = Order.createOrder(TEST_USER_ID, null, 0L, TEST_SUBTOTAL, TEST_SUBTOTAL);

            // When & Then
            assertEquals(0, order.getOrderItemCount());

            // When
            order.addOrderItem(OrderItem.createOrderItem(100L, 1L, "상품1", "옵션1", 2, 50000L));
            order.addOrderItem(OrderItem.createOrderItem(200L, 2L, "상품2", "옵션2", 3, 30000L));

            // Then
            assertEquals(2, order.getOrderItemCount());
        }

        @Test
        @DisplayName("getTotalQuantity - 주문 총 수량 계산")
        void testGetTotalQuantity() {
            // Given
            Order order = Order.createOrder(TEST_USER_ID, null, 0L, TEST_SUBTOTAL, TEST_SUBTOTAL);
            order.addOrderItem(OrderItem.createOrderItem(100L, 1L, "상품1", "옵션1", 2, 50000L));
            order.addOrderItem(OrderItem.createOrderItem(200L, 2L, "상품2", "옵션2", 3, 30000L));
            order.addOrderItem(OrderItem.createOrderItem(300L, 3L, "상품3", "옵션3", 5, 20000L));

            // When
            Integer totalQuantity = order.getTotalQuantity();

            // Then
            assertEquals(10, totalQuantity);  // 2 + 3 + 5
        }

        @Test
        @DisplayName("addOrderItem - null 항목 추가 실패")
        void testAddOrderItem_NullItem() {
            // Given
            Order order = Order.createOrder(TEST_USER_ID, null, 0L, TEST_SUBTOTAL, TEST_SUBTOTAL);

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> order.addOrderItem(null));
        }

        @Test
        @DisplayName("getTotalQuantity - 빈 주문의 총 수량은 0")
        void testGetTotalQuantity_EmptyOrder() {
            // Given
            Order order = Order.createOrder(TEST_USER_ID, null, 0L, TEST_SUBTOTAL, TEST_SUBTOTAL);

            // When
            Integer totalQuantity = order.getTotalQuantity();

            // Then
            assertEquals(0, totalQuantity);
        }
    }

    // ========== 비즈니스 규칙 검증 ==========

    @Nested
    @DisplayName("Order 생성 시 비즈니스 규칙 검증")
    class OrderCreationValidationTests {

        @Test
        @DisplayName("Order 생성 - 음수 최종 금액 거절")
        void testCreateOrder_NegativeFinalAmount() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () ->
                Order.createOrder(TEST_USER_ID, TEST_COUPON_ID, 120000L, 100000L, -20000L)
            );
        }

        @Test
        @DisplayName("Order 생성 - 음수 쿠폰 할인액 거절")
        void testCreateOrder_NegativeCouponDiscount() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () ->
                Order.createOrder(TEST_USER_ID, TEST_COUPON_ID, -5000L, 100000L, 100000L)
            );
        }

        @Test
        @DisplayName("Order 생성 - 0원 쿠폰 할인액은 허용")
        void testCreateOrder_ZeroCouponDiscount() {
            // When
            Order order = Order.createOrder(TEST_USER_ID, TEST_COUPON_ID, 0L, 100000L, 100000L);

            // Then
            assertEquals(0L, order.getCouponDiscount());
            assertEquals(100000L, order.getFinalAmount());
        }

        @Test
        @DisplayName("Order 생성 - 0원 최종 금액은 허용")
        void testCreateOrder_ZeroFinalAmount() {
            // When
            Order order = Order.createOrder(TEST_USER_ID, TEST_COUPON_ID, 100000L, 100000L, 0L);

            // Then
            assertEquals(0L, order.getFinalAmount());
        }
    }

    // ========== 실제 비즈니스 시나리오 테스트 ==========

    @Nested
    @DisplayName("실제 비즈니스 시나리오")
    class RealWorldScenarios {

        @Test
        @DisplayName("시나리오 1: 쿠폰 적용 주문 생성 및 취소")
        void scenario1_CreateOrderWithCouponAndCancel() {
            // Given: 사용자가 100,000원 상품 2개 구매 (쿠폰으로 5,000원 할인)
            Long userId = 1L;
            Long subtotal = 200000L;  // 100,000 × 2
            Long couponDiscount = 5000L;
            Long finalAmount = 195000L;

            // When: 주문 생성
            Order order = Order.createOrder(userId, 1L, couponDiscount, subtotal, finalAmount);
            order.addOrderItem(OrderItem.createOrderItem(1L, 1L, "상품1", "색상-빨강", 2, 100000L));

            // Then: 주문 상태 확인
            assertTrue(order.isCompleted());
            assertTrue(order.hasCoupon());
            assertEquals(195000L, order.getPaymentAmount());
            assertEquals(2, order.getTotalQuantity());

            // When: 주문 취소
            order.cancel();

            // Then: 취소 완료
            assertTrue(order.isCancelled());
            assertNotNull(order.getCancelledAt());
        }

        @Test
        @DisplayName("시나리오 2: 여러 상품 주문 및 총액 검증")
        void scenario2_MultipleProductsOrderWithTotalValidation() {
            // Given: 여러 상품 주문
            Order order = Order.createOrder(1L, null, 0L, 0L, 0L);

            // When: 상품 항목 추가
            order.addOrderItem(OrderItem.createOrderItem(1L, 1L, "노트북", "검은색", 1, 1500000L));
            order.addOrderItem(OrderItem.createOrderItem(2L, 2L, "마우스", "회색", 2, 50000L));
            order.addOrderItem(OrderItem.createOrderItem(3L, 3L, "키보드", "검은색", 1, 150000L));

            // Then: 수량과 항목 수 검증
            assertEquals(4, order.getTotalQuantity());  // 1 + 2 + 1
            assertEquals(3, order.getOrderItemCount());
        }

        @Test
        @DisplayName("시나리오 3: 전체 할인율 50% 이상인 주문")
        void scenario3_HighDiscountOrderPercentage() {
            // Given: 소계 100,000원에 60,000원 할인 (60%)
            Order order = Order.createOrder(1L, 1L, 60000L, 100000L, 40000L);

            // Then: 할인율 검증
            assertEquals(60.0, order.getDiscountPercentage());
            assertEquals(40000L, order.getPaymentAmount());
        }
    }
}
