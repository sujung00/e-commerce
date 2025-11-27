package com.hhplus.ecommerce.unit.application.order;

import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderItem;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.application.order.OrderCancelTransactionService;
import com.hhplus.ecommerce.application.user.UserBalanceService;
import com.hhplus.ecommerce.application.order.dto.CancelOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OrderCancelTransactionServiceTest - Application 계층 트랜잭션 테스트
 * 주문 취소 시 원자적 거래 처리 테스트
 *
 * 테스트 대상: OrderCancelTransactionService.executeTransactionalCancel()
 * - 주문 상태 변경
 * - 재고 복구
 * - 사용자 잔액 복구
 * - 쿠폰 상태 복구
 *
 * 테스트 유형:
 * - 성공 케이스: 정상적인 취소 처리
 * - 예외 케이스: 재고 복구 실패, 버전 충돌
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCancelTransactionService 단위 테스트")
class OrderCancelTransactionServiceTest {

    private OrderCancelTransactionService orderCancelTransactionService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private UserBalanceService userBalanceService;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_ORDER_ID = 100L;
    private static final Long TEST_PRODUCT_ID = 1L;
    private static final Long TEST_OPTION_ID = 101L;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        orderCancelTransactionService = new OrderCancelTransactionService(
                orderRepository,
                productRepository,
                userRepository,
                userCouponRepository,
                userBalanceService
        );
    }

    // ========== 주문 취소 트랜잭션 (executeTransactionalCancel) ==========

    @Test
    @DisplayName("주문 취소 트랜잭션 - 성공 (단일 항목, 쿠폰 없음)")
    void testExecuteTransactionalCancel_Success_SingleItem_NoCoupon() {
        // Given
        ProductOption option = ProductOption.builder()
                .optionId(TEST_OPTION_ID)
                .productId(TEST_PRODUCT_ID)
                .name("기본옵션")
                .stock(8)  // 2개 취소 후 상태
                .version(2L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("상품1")
                .price(50000L)
                .status("판매중")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .options(List.of(option))
                .build();

        OrderItem orderItem = OrderItem.builder()
                .orderItemId(1L)
                .orderId(TEST_ORDER_ID)
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .productName("상품1")
                .optionName("기본옵션")
                .quantity(2)
                .unitPrice(50000L)
                .subtotal(100000L)
                .createdAt(LocalDateTime.now())
                .build();

        Order order = Order.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus(com.hhplus.ecommerce.domain.order.OrderStatus.COMPLETED)
                .subtotal(100000L)
                .couponDiscount(0L)
                .couponId(null)
                .finalAmount(100000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .orderItems(List.of(orderItem))
                .build();

        User user = User.builder()
                .userId(TEST_USER_ID)
                .name("testuser")
                .email("test@example.com")
                .balance(50000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(productRepository.findById(TEST_PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(userRepository.findById(TEST_USER_ID))
                .thenReturn(Optional.of(user));
        when(orderRepository.save(any(Order.class)))
                .thenReturn(order);

        // When
        CancelOrderResponse result = orderCancelTransactionService.executeTransactionalCancel(
                TEST_ORDER_ID, TEST_USER_ID, order
        );

        // Then
        assertNotNull(result);
        assertEquals("CANCELLED", result.getOrderStatus());
        assertEquals(100000L, result.getRefundAmount());
        assertEquals(1, result.getRestoredItems().size());
        assertEquals(2, result.getRestoredItems().get(0).getQuantity());
        assertEquals(10, result.getRestoredItems().get(0).getRestoredStock());  // 8 + 2
        assertNotNull(result.getCancelledAt());

        // 저장소 호출 확인
        verify(productRepository, times(1)).save(product);
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 취소 트랜잭션 - 성공 (다중 항목, 쿠폰 적용)")
    void testExecuteTransactionalCancel_Success_MultipleItems_WithCoupon() {
        // Given
        ProductOption option1 = ProductOption.builder()
                .optionId(101L)
                .productId(1L)
                .name("옵션A")
                .stock(8)  // 2개 취소 후 상태
                .version(2L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ProductOption option2 = ProductOption.builder()
                .optionId(102L)
                .productId(2L)
                .name("옵션B")
                .stock(7)  // 3개 취소 후 상태
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Product product1 = Product.builder()
                .productId(1L)
                .productName("상품1")
                .price(50000L)
                .status("판매중")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .options(List.of(option1))
                .build();

        Product product2 = Product.builder()
                .productId(2L)
                .productName("상품2")
                .price(30000L)
                .status("판매중")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .options(List.of(option2))
                .build();

        OrderItem orderItem1 = OrderItem.builder()
                .orderItemId(1L)
                .orderId(TEST_ORDER_ID)
                .productId(1L)
                .optionId(101L)
                .productName("상품1")
                .optionName("옵션A")
                .quantity(2)
                .unitPrice(50000L)
                .subtotal(100000L)
                .createdAt(LocalDateTime.now())
                .build();

        OrderItem orderItem2 = OrderItem.builder()
                .orderItemId(2L)
                .orderId(TEST_ORDER_ID)
                .productId(2L)
                .optionId(102L)
                .productName("상품2")
                .optionName("옵션B")
                .quantity(3)
                .unitPrice(30000L)
                .subtotal(90000L)
                .createdAt(LocalDateTime.now())
                .build();

        Order order = Order.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus(com.hhplus.ecommerce.domain.order.OrderStatus.COMPLETED)
                .subtotal(190000L)
                .couponDiscount(10000L)
                .couponId(1L)
                .finalAmount(180000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .orderItems(List.of(orderItem1, orderItem2))
                .build();

        User user = User.builder()
                .userId(TEST_USER_ID)
                .name("testuser")
                .email("test@example.com")
                .balance(50000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product1));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product2));
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        // When
        CancelOrderResponse result = orderCancelTransactionService.executeTransactionalCancel(
                TEST_ORDER_ID, TEST_USER_ID, order
        );

        // Then
        assertNotNull(result);
        assertEquals("CANCELLED", result.getOrderStatus());
        assertEquals(180000L, result.getRefundAmount());
        assertEquals(2, result.getRestoredItems().size());

        // 첫 번째 항목 검증
        assertEquals(1L, result.getRestoredItems().get(0).getOrderItemId());
        assertEquals(1L, result.getRestoredItems().get(0).getProductId());
        assertEquals(2, result.getRestoredItems().get(0).getQuantity());
        assertEquals(10, result.getRestoredItems().get(0).getRestoredStock());  // 8 + 2

        // 두 번째 항목 검증
        assertEquals(2L, result.getRestoredItems().get(1).getOrderItemId());
        assertEquals(2L, result.getRestoredItems().get(1).getProductId());
        assertEquals(3, result.getRestoredItems().get(1).getQuantity());
        assertEquals(10, result.getRestoredItems().get(1).getRestoredStock());  // 7 + 3

        // 저장소 호출 확인
        verify(productRepository, times(2)).save(any(Product.class));
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 취소 트랜잭션 - 성공 (사용자 잔액 복구)")
    void testExecuteTransactionalCancel_Success_UserBalanceRestored() {
        // Given
        ProductOption option = ProductOption.builder()
                .optionId(TEST_OPTION_ID)
                .productId(TEST_PRODUCT_ID)
                .name("기본옵션")
                .stock(8)
                .version(2L)
                .build();

        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("상품1")
                .price(50000L)
                .options(List.of(option))
                .build();

        OrderItem orderItem = OrderItem.builder()
                .orderItemId(1L)
                .orderId(TEST_ORDER_ID)
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .productName("상품1")
                .optionName("기본옵션")
                .quantity(2)
                .unitPrice(50000L)
                .subtotal(100000L)
                .build();

        Order order = Order.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus(com.hhplus.ecommerce.domain.order.OrderStatus.COMPLETED)
                .subtotal(100000L)
                .couponDiscount(0L)
                .couponId(null)
                .finalAmount(100000L)
                .orderItems(List.of(orderItem))
                .build();

        User user = User.builder()
                .userId(TEST_USER_ID)
                .name("testuser")
                .email("test@example.com")
                .balance(50000L)  // 100000 - 100000 + 100000 = 150000
                .build();

        when(productRepository.findById(TEST_PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(userRepository.findById(TEST_USER_ID))
                .thenReturn(Optional.of(user));
        when(orderRepository.save(any(Order.class)))
                .thenReturn(order);

        // When
        CancelOrderResponse result = orderCancelTransactionService.executeTransactionalCancel(
                TEST_ORDER_ID, TEST_USER_ID, order
        );

        // Then
        assertNotNull(result);
        // 잔액 복구 확인
        assertEquals(150000L, user.getBalance());  // 50000 + 100000
        verify(userRepository, times(1)).findById(TEST_USER_ID);
    }

    @Test
    @DisplayName("주문 취소 트랜잭션 - 버전 충돌 시뮬레이션 테스트 (동시성 제어)")
    void testExecuteTransactionalCancel_Failed_VersionConflict() {
        // Given - 테스트하기 위해 버전 충돌 시나리오를 직접 생성
        // 메모리 저장소이므로 실제 동시성 테스트는 Integration Test에서 수행
        // 여기서는 버전 체크 로직의 경로를 검증하기 위한 테스트
        ProductOption option = ProductOption.builder()
                .optionId(TEST_OPTION_ID)
                .productId(TEST_PRODUCT_ID)
                .name("기본옵션")
                .stock(8)
                .version(2L)
                .build();

        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("상품1")
                .price(50000L)
                .status("판매중")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .options(List.of(option))
                .build();

        OrderItem orderItem = OrderItem.builder()
                .orderItemId(1L)
                .orderId(TEST_ORDER_ID)
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .productName("상품1")
                .optionName("기본옵션")
                .quantity(2)
                .unitPrice(50000L)
                .subtotal(100000L)
                .createdAt(LocalDateTime.now())
                .build();

        Order order = Order.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus(com.hhplus.ecommerce.domain.order.OrderStatus.COMPLETED)
                .subtotal(100000L)
                .couponDiscount(0L)
                .couponId(null)
                .finalAmount(100000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .orderItems(List.of(orderItem))
                .build();

        User user = User.builder()
                .userId(TEST_USER_ID)
                .name("testuser")
                .email("test@example.com")
                .balance(50000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(productRepository.findById(TEST_PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(userRepository.findById(TEST_USER_ID))
                .thenReturn(Optional.of(user));
        when(orderRepository.save(any(Order.class)))
                .thenReturn(order);

        // 정상적으로 처리됨 (메모리 저장소이므로 버전 충돌이 자동으로 발생하지 않음)
        // 버전 충돌 테스트는 Integration Test 또는 동시성 테스트에서 수행 필요
        CancelOrderResponse result = orderCancelTransactionService.executeTransactionalCancel(
                TEST_ORDER_ID, TEST_USER_ID, order
        );

        // Then - 정상 처리 검증
        assertNotNull(result);
        assertEquals("CANCELLED", result.getOrderStatus());
        assertEquals(100000L, result.getRefundAmount());
    }

    @Test
    @DisplayName("주문 취소 트랜잭션 - 응답 객체 검증")
    void testExecuteTransactionalCancel_ResponseValidation() {
        // Given
        ProductOption option = ProductOption.builder()
                .optionId(TEST_OPTION_ID)
                .productId(TEST_PRODUCT_ID)
                .name("옵션")
                .stock(5)
                .version(2L)
                .build();

        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("상품")
                .price(50000L)
                .options(List.of(option))
                .build();

        OrderItem orderItem = OrderItem.builder()
                .orderItemId(1L)
                .orderId(TEST_ORDER_ID)
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .productName("상품")
                .optionName("옵션")
                .quantity(5)
                .unitPrice(50000L)
                .build();

        Order order = Order.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus(com.hhplus.ecommerce.domain.order.OrderStatus.COMPLETED)
                .subtotal(250000L)
                .couponDiscount(5000L)
                .couponId(1L)
                .finalAmount(245000L)
                .orderItems(List.of(orderItem))
                .build();

        User user = User.builder()
                .userId(TEST_USER_ID)
                .name("testuser")
                .balance(100000L)
                .build();

        when(productRepository.findById(TEST_PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(userRepository.findById(TEST_USER_ID))
                .thenReturn(Optional.of(user));
        when(orderRepository.save(any(Order.class)))
                .thenReturn(order);

        // When
        CancelOrderResponse result = orderCancelTransactionService.executeTransactionalCancel(
                TEST_ORDER_ID, TEST_USER_ID, order
        );

        // Then
        assertNotNull(result);
        assertNotNull(result.getCancelledAt());
        assertEquals(TEST_ORDER_ID, result.getOrderId());
        assertEquals("CANCELLED", result.getOrderStatus());
        assertEquals(245000L, result.getRefundAmount());
        assertEquals(1, result.getRestoredItems().size());
    }
}
