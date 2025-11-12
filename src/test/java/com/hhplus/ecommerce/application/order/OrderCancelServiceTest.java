package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderNotFoundException;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.application.order.dto.CancelOrderResponse;
import com.hhplus.ecommerce.domain.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * OrderCancelServiceTest - Application 계층 단위 테스트
 * 주문 취소 비즈니스 로직 테스트
 *
 * 테스트 대상: OrderService.cancelOrder()
 * - 주문 검증 (존재 여부, 권한, 상태)
 * - OrderCancelTransactionService 위임
 *
 * 테스트 유형:
 * - 성공 케이스: 정상적인 주문 취소
 * - 예외 케이스: 주문 없음, 권한 없음, 상태 오류
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCancelService 단위 테스트")
class OrderCancelServiceTest {

    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderTransactionService orderTransactionService;

    @Mock
    private OrderCancelTransactionService orderCancelTransactionService;

    @Mock
    private OrderValidator orderValidator;

    @Mock
    private OrderCalculator orderCalculator;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_ORDER_ID = 100L;
    private static final Long TEST_PRODUCT_ID = 1L;
    private static final Long TEST_OPTION_ID = 101L;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        orderService = new OrderService(
                orderRepository,
                userRepository,
                orderValidator,
                orderCalculator,
                orderTransactionService,
                orderCancelTransactionService
        );
    }

    // ========== 주문 취소 (cancelOrder) ==========

    @Test
    @DisplayName("주문 취소 - 성공 (쿠폰 적용)")
    void testCancelOrder_Success_WithCoupon() {
        // Given
        Order order = Order.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus(com.hhplus.ecommerce.domain.order.OrderStatus.COMPLETED)
                .subtotal(100000L)
                .couponDiscount(5000L)
                .couponId(1L)
                .finalAmount(95000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .orderItems(new ArrayList<>())
                .build();

        CancelOrderResponse response = CancelOrderResponse.builder()
                .orderId(TEST_ORDER_ID)
                .orderStatus("COMPLETED")
                .refundAmount(95000L)  // 환불액 = finalAmount
                .cancelledAt(LocalDateTime.now())
                .restoredItems(new ArrayList<>())
                .build();

        when(orderRepository.findById(TEST_ORDER_ID))
                .thenReturn(Optional.of(order));
        when(orderCancelTransactionService.executeTransactionalCancel(TEST_ORDER_ID, TEST_USER_ID, order))
                .thenReturn(response);

        // When
        CancelOrderResponse result = orderService.cancelOrder(TEST_USER_ID, TEST_ORDER_ID);

        // Then
        assertNotNull(result);
        assertEquals("COMPLETED", result.getOrderStatus());
        assertEquals(95000L, result.getRefundAmount());  // 환불액 검증
        verify(orderRepository, times(1)).findById(TEST_ORDER_ID);
        verify(orderCancelTransactionService, times(1)).executeTransactionalCancel(TEST_ORDER_ID, TEST_USER_ID, order);
    }

    @Test
    @DisplayName("주문 취소 - 성공 (쿠폰 미적용)")
    void testCancelOrder_Success_NoCoupon() {
        // Given
        Order order = Order.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus(com.hhplus.ecommerce.domain.order.OrderStatus.COMPLETED)
                .subtotal(50000L)
                .couponDiscount(0L)
                .couponId(null)
                .finalAmount(50000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .orderItems(new ArrayList<>())
                .build();

        CancelOrderResponse response = CancelOrderResponse.builder()
                .orderId(TEST_ORDER_ID)
                .orderStatus("COMPLETED")
                .refundAmount(50000L)  // 환불액 = finalAmount (쿠폰 미적용)
                .cancelledAt(LocalDateTime.now())
                .restoredItems(new ArrayList<>())
                .build();

        when(orderRepository.findById(TEST_ORDER_ID))
                .thenReturn(Optional.of(order));
        when(orderCancelTransactionService.executeTransactionalCancel(TEST_ORDER_ID, TEST_USER_ID, order))
                .thenReturn(response);

        // When
        CancelOrderResponse result = orderService.cancelOrder(TEST_USER_ID, TEST_ORDER_ID);

        // Then
        assertNotNull(result);
        assertEquals(50000L, result.getRefundAmount());  // 쿠폰 적용 없이 전액 환불
        verify(orderCancelTransactionService, times(1)).executeTransactionalCancel(TEST_ORDER_ID, TEST_USER_ID, order);
    }

    @Test
    @DisplayName("주문 취소 - 실패 (주문 없음)")
    void testCancelOrder_Failed_OrderNotFound() {
        // Given
        when(orderRepository.findById(TEST_ORDER_ID))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(OrderNotFoundException.class, () -> {
            orderService.cancelOrder(TEST_USER_ID, TEST_ORDER_ID);
        });

        verify(orderRepository, times(1)).findById(TEST_ORDER_ID);
        verify(orderCancelTransactionService, never()).executeTransactionalCancel(anyLong(), anyLong(), any(Order.class));
    }

    @Test
    @DisplayName("주문 취소 - 실패 (권한 없음)")
    void testCancelOrder_Failed_Unauthorized() {
        // Given
        Long otherUserId = 999L;
        Order order = Order.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)  // 다른 사용자의 주문
                .orderStatus(com.hhplus.ecommerce.domain.order.OrderStatus.COMPLETED)
                .subtotal(100000L)
                .couponDiscount(0L)
                .couponId(null)
                .finalAmount(100000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .orderItems(new ArrayList<>())
                .build();

        when(orderRepository.findById(TEST_ORDER_ID))
                .thenReturn(Optional.of(order));

        // When & Then
        assertThrows(com.hhplus.ecommerce.domain.order.UserMismatchException.class, () -> {
            orderService.cancelOrder(otherUserId, TEST_ORDER_ID);
        });

        verify(orderCancelTransactionService, never()).executeTransactionalCancel(anyLong(), anyLong(), any(Order.class));
    }

    @Test
    @DisplayName("주문 취소 - 실패 (주문 상태 오류: PENDING)")
    void testCancelOrder_Failed_InvalidStatus_Pending() {
        // Given
        Order order = Order.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus(com.hhplus.ecommerce.domain.order.OrderStatus.PENDING)  // COMPLETED가 아님
                .subtotal(100000L)
                .couponDiscount(0L)
                .couponId(null)
                .finalAmount(100000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .orderItems(new ArrayList<>())
                .build();

        when(orderRepository.findById(TEST_ORDER_ID))
                .thenReturn(Optional.of(order));

        // When & Then
        assertThrows(com.hhplus.ecommerce.domain.order.InvalidOrderStatusException.class, () -> {
            orderService.cancelOrder(TEST_USER_ID, TEST_ORDER_ID);
        });

        verify(orderCancelTransactionService, never()).executeTransactionalCancel(anyLong(), anyLong(), any(Order.class));
    }

    @Test
    @DisplayName("주문 취소 - 실패 (주문 상태 오류: CANCELLED)")
    void testCancelOrder_Failed_InvalidStatus_AlreadyCancelled() {
        // Given
        Order order = Order.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus(com.hhplus.ecommerce.domain.order.OrderStatus.CANCELLED)  // 이미 취소됨
                .subtotal(100000L)
                .couponDiscount(0L)
                .couponId(null)
                .finalAmount(100000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .orderItems(new ArrayList<>())
                .build();

        when(orderRepository.findById(TEST_ORDER_ID))
                .thenReturn(Optional.of(order));

        // When & Then
        assertThrows(com.hhplus.ecommerce.domain.order.InvalidOrderStatusException.class, () -> {
            orderService.cancelOrder(TEST_USER_ID, TEST_ORDER_ID);
        });

        verify(orderCancelTransactionService, never()).executeTransactionalCancel(anyLong(), anyLong(), any(Order.class));
    }

    @Test
    @DisplayName("주문 취소 - 검증 통과 후 트랜잭션 위임")
    void testCancelOrder_ValidationPassedThenDelegateToTransaction() {
        // Given
        Order order = Order.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus(com.hhplus.ecommerce.domain.order.OrderStatus.COMPLETED)
                .subtotal(100000L)
                .couponDiscount(5000L)
                .couponId(1L)
                .finalAmount(95000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .orderItems(new ArrayList<>())
                .build();

        CancelOrderResponse response = CancelOrderResponse.builder()
                .orderId(TEST_ORDER_ID)
                .orderStatus("COMPLETED")
                .refundAmount(95000L)  // 환불액 = finalAmount
                .cancelledAt(LocalDateTime.now())
                .restoredItems(new ArrayList<>())
                .build();

        when(orderRepository.findById(TEST_ORDER_ID))
                .thenReturn(Optional.of(order));
        when(orderCancelTransactionService.executeTransactionalCancel(TEST_ORDER_ID, TEST_USER_ID, order))
                .thenReturn(response);

        // When
        CancelOrderResponse result = orderService.cancelOrder(TEST_USER_ID, TEST_ORDER_ID);

        // Then
        assertNotNull(result);
        // 트랜잭션 서비스가 호출되었는지 확인
        verify(orderCancelTransactionService, times(1)).executeTransactionalCancel(TEST_ORDER_ID, TEST_USER_ID, order);
    }
}
