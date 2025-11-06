package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderNotFoundException;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductNotFoundException;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.presentation.order.request.CreateOrderRequest;
import com.hhplus.ecommerce.presentation.order.request.OrderItemRequest;
import com.hhplus.ecommerce.presentation.order.response.CreateOrderResponse;
import com.hhplus.ecommerce.presentation.order.response.OrderDetailResponse;
import com.hhplus.ecommerce.presentation.order.response.OrderListResponse;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * OrderServiceTest - Application 계층 단위 테스트
 * Spring Boot 3.4+ Mockito 방식 테스트
 *
 * 테스트 대상: OrderService
 * - 주문 생성 (3단계: 검증 → 트랜잭션 → 후처리)
 * - 주문 상세 조회
 * - 주문 목록 조회 (페이지네이션, 상태 필터링)
 *
 * 테스트 유형:
 * - 성공 케이스: 정상적인 주문 생성, 조회
 * - 트랜잭션 처리: OrderTransactionService 위임 검증
 * - 예외 케이스: 사용자 검증, 상품 검증, 금액 계산
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 단위 테스트")
class OrderServiceTest {

    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderTransactionService orderTransactionService;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_ORDER_ID = 100L;
    private static final Long TEST_PRODUCT_ID = 1L;
    private static final Long TEST_OPTION_ID = 101L;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        orderService = new OrderService(orderRepository, productRepository, userRepository, orderTransactionService);
    }

    // ========== 주문 생성 (createOrder) ==========

    @Test
    @DisplayName("주문 생성 - 성공 (쿠폰 미적용)")
    void testCreateOrder_Success_NoCoupon() {
        // Given
        User user = User.builder()
                .userId(TEST_USER_ID)
                .name("testuser")
                .email("test@example.com")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ProductOption option = ProductOption.builder()
                .optionId(TEST_OPTION_ID)
                .productId(TEST_PRODUCT_ID)
                .name("기본옵션")
                .stock(10)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("상품1")
                .description("상품1 설명")
                .price(100000L)
                .options(List.of(option))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderItems(List.of(
                        OrderItemRequest.builder()
                                .productId(TEST_PRODUCT_ID)
                                .optionId(TEST_OPTION_ID)
                                .quantity(1)
                                .build()
                ))
                .couponId(null)
                .build();

        Order savedOrder = Order.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus("PENDING")
                .subtotal(100000L)
                .couponDiscount(0L)
                .finalAmount(100000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
        when(orderTransactionService.executeTransactionalOrder(
                anyLong(), anyList(), anyLong(), anyLong(), anyLong(), anyLong()
        )).thenReturn(savedOrder);

        // When
        CreateOrderResponse result = orderService.createOrder(TEST_USER_ID, request);

        // Then
        assertNotNull(result);
        assertEquals(TEST_USER_ID, result.getUserId());
        assertEquals(100000L, result.getSubtotal());
        assertEquals(0L, result.getCouponDiscount());
        assertEquals(100000L, result.getFinalAmount());

        verify(userRepository, times(1)).findById(TEST_USER_ID);
        verify(orderTransactionService, times(1)).executeTransactionalOrder(
                anyLong(), anyList(), anyLong(), anyLong(), anyLong(), anyLong()
        );
    }

    @Test
    @DisplayName("주문 생성 - 성공 (쿠폰 적용)")
    void testCreateOrder_Success_WithCoupon() {
        // Given
        User user = User.builder()
                .userId(TEST_USER_ID)
                .name("testuser")
                .email("test@example.com")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ProductOption option = ProductOption.builder()
                .optionId(TEST_OPTION_ID)
                .productId(TEST_PRODUCT_ID)
                .name("기본옵션")
                .stock(10)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("상품1")
                .description("상품1 설명")
                .price(100000L)
                .options(List.of(option))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderItems(List.of(
                        OrderItemRequest.builder()
                                .productId(TEST_PRODUCT_ID)
                                .optionId(TEST_OPTION_ID)
                                .quantity(1)
                                .build()
                ))
                .couponId(1L)
                .build();

        Order savedOrder = Order.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus("PENDING")
                .subtotal(100000L)
                .couponDiscount(5000L)
                .finalAmount(95000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
        when(orderTransactionService.executeTransactionalOrder(
                anyLong(), anyList(), anyLong(), anyLong(), anyLong(), anyLong()
        )).thenReturn(savedOrder);

        // When
        CreateOrderResponse result = orderService.createOrder(TEST_USER_ID, request);

        // Then
        assertNotNull(result);
        assertEquals(100000L, result.getSubtotal());
        assertEquals(5000L, result.getCouponDiscount());
        assertEquals(95000L, result.getFinalAmount());

        verify(orderTransactionService, times(1)).executeTransactionalOrder(
                anyLong(), anyList(), anyLong(), anyLong(), anyLong(), anyLong()
        );
    }

    @Test
    @DisplayName("주문 생성 - 실패 (사용자 없음)")
    void testCreateOrder_Failed_UserNotFound() {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderItems(List.of(
                        OrderItemRequest.builder()
                                .productId(TEST_PRODUCT_ID)
                                .optionId(TEST_OPTION_ID)
                                .quantity(1)
                                .build()
                ))
                .couponId(null)
                .build();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(UserNotFoundException.class, () -> {
            orderService.createOrder(TEST_USER_ID, request);
        });

        verify(userRepository, times(1)).findById(TEST_USER_ID);
        verify(orderTransactionService, never()).executeTransactionalOrder(
                anyLong(), anyList(), anyLong(), anyLong(), anyLong(), anyLong()
        );
    }

    @Test
    @DisplayName("주문 생성 - 실패 (상품 없음)")
    void testCreateOrder_Failed_ProductNotFound() {
        // Given
        User user = User.builder()
                .userId(TEST_USER_ID)
                .name("testuser")
                .email("test@example.com")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderItems(List.of(
                        OrderItemRequest.builder()
                                .productId(TEST_PRODUCT_ID)
                                .optionId(TEST_OPTION_ID)
                                .quantity(1)
                                .build()
                ))
                .couponId(null)
                .build();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ProductNotFoundException.class, () -> {
            orderService.createOrder(TEST_USER_ID, request);
        });
    }

    @Test
    @DisplayName("주문 생성 - 실패 (빈 주문 항목)")
    void testCreateOrder_Failed_EmptyOrderItems() {
        // Given
        User user = User.builder()
                .userId(TEST_USER_ID)
                .name("testuser")
                .email("test@example.com")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderItems(new ArrayList<>())
                .couponId(null)
                .build();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            orderService.createOrder(TEST_USER_ID, request);
        });
    }

    // ========== 주문 상세 조회 (getOrderDetail) ==========

    @Test
    @DisplayName("주문 상세 조회 - 성공")
    void testGetOrderDetail_Success() {
        // Given
        Order order = Order.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus("CONFIRMED")
                .subtotal(150000L)
                .couponDiscount(10000L)
                .finalAmount(140000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(orderRepository.findById(TEST_ORDER_ID)).thenReturn(Optional.of(order));

        // When
        OrderDetailResponse result = orderService.getOrderDetail(TEST_USER_ID, TEST_ORDER_ID);

        // Then
        assertNotNull(result);
        assertEquals(TEST_ORDER_ID, result.getOrderId());
        assertEquals(TEST_USER_ID, result.getUserId());
        assertEquals("CONFIRMED", result.getOrderStatus());
        assertEquals(150000L, result.getSubtotal());
        assertEquals(10000L, result.getCouponDiscount());
        assertEquals(140000L, result.getFinalAmount());

        verify(orderRepository, times(1)).findById(TEST_ORDER_ID);
    }

    @Test
    @DisplayName("주문 상세 조회 - 실패 (주문 없음)")
    void testGetOrderDetail_Failed_OrderNotFound() {
        // Given
        when(orderRepository.findById(TEST_ORDER_ID)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(OrderNotFoundException.class, () -> {
            orderService.getOrderDetail(TEST_USER_ID, TEST_ORDER_ID);
        });
    }

    // ========== 주문 목록 조회 (getOrderList) ==========

    @Test
    @DisplayName("주문 목록 조회 - 성공 (기본 파라미터)")
    void testGetOrderList_Success_DefaultParameters() {
        // Given
        List<Order> orders = List.of(
                Order.builder()
                        .orderId(100L)
                        .userId(TEST_USER_ID)
                        .orderStatus("CONFIRMED")
                        .subtotal(100000L)
                        .couponDiscount(5000L)
                        .finalAmount(95000L)
                        .createdAt(LocalDateTime.now().minusDays(1))
                        .updatedAt(LocalDateTime.now())
                        .build(),
                Order.builder()
                        .orderId(101L)
                        .userId(TEST_USER_ID)
                        .orderStatus("PENDING")
                        .subtotal(50000L)
                        .couponDiscount(0L)
                        .finalAmount(50000L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );

        when(orderRepository.findByUserId(TEST_USER_ID, 0, 10)).thenReturn(orders);
        when(orderRepository.countByUserId(TEST_USER_ID)).thenReturn(2L);

        // When
        OrderListResponse result = orderService.getOrderList(TEST_USER_ID, 0, 10, Optional.empty());

        // Then
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals(2L, result.getTotalElements());

        verify(orderRepository, times(1)).findByUserId(TEST_USER_ID, 0, 10);
    }

    @Test
    @DisplayName("주문 목록 조회 - 성공 (페이지네이션)")
    void testGetOrderList_Success_Pagination() {
        // Given
        List<Order> orders = List.of(
                Order.builder()
                        .orderId(100L)
                        .userId(TEST_USER_ID)
                        .orderStatus("CONFIRMED")
                        .subtotal(100000L)
                        .couponDiscount(5000L)
                        .finalAmount(95000L)
                        .createdAt(LocalDateTime.now().minusDays(1))
                        .updatedAt(LocalDateTime.now())
                        .build()
        );

        when(orderRepository.findByUserId(TEST_USER_ID, 0, 10))
                .thenReturn(orders);
        when(orderRepository.countByUserId(TEST_USER_ID))
                .thenReturn(1L);

        // When
        OrderListResponse result = orderService.getOrderList(TEST_USER_ID, 0, 10, Optional.empty());

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("CONFIRMED", result.getContent().get(0).getOrderStatus());

        verify(orderRepository, times(1)).findByUserId(TEST_USER_ID, 0, 10);
    }

    @Test
    @DisplayName("주문 목록 조회 - 성공 (빈 결과)")
    void testGetOrderList_Success_EmptyResult() {
        // Given
        when(orderRepository.findByUserId(TEST_USER_ID, 0, 10)).thenReturn(new ArrayList<>());
        when(orderRepository.countByUserId(TEST_USER_ID)).thenReturn(0L);

        // When
        OrderListResponse result = orderService.getOrderList(TEST_USER_ID, 0, 10, Optional.empty());

        // Then
        assertNotNull(result);
        assertEquals(0, result.getContent().size());
        assertEquals(0L, result.getTotalElements());
    }

    // ========== 금액 계산 검증 ==========

    @Test
    @DisplayName("주문 생성 - 금액 계산 검증 (단일 상품)")
    void testCreateOrder_PriceCalculation_SingleProduct() {
        // Given
        User user = User.builder()
                .userId(TEST_USER_ID)
                .name("testuser")
                .email("test@example.com")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Product product = createProductWithOption(TEST_PRODUCT_ID, "상품1", 50000L);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderItems(List.of(
                        OrderItemRequest.builder()
                                .productId(TEST_PRODUCT_ID)
                                .optionId(TEST_OPTION_ID)
                                .quantity(1)
                                .build()
                ))
                .couponId(null)
                .build();

        Order savedOrder = Order.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus("PENDING")
                .subtotal(50000L)
                .couponDiscount(0L)
                .finalAmount(50000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
        when(orderTransactionService.executeTransactionalOrder(
                anyLong(), anyList(), anyLong(), anyLong(), anyLong(), anyLong()
        )).thenReturn(savedOrder);

        // When
        CreateOrderResponse result = orderService.createOrder(TEST_USER_ID, request);

        // Then
        assertEquals(50000L, result.getSubtotal());
        assertEquals(50000L, result.getFinalAmount());
    }

    @Test
    @DisplayName("주문 생성 - 금액 계산 검증 (다중 상품)")
    void testCreateOrder_PriceCalculation_MultipleProducts() {
        // Given
        User user = User.builder()
                .userId(TEST_USER_ID)
                .name("testuser")
                .email("test@example.com")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Product product1 = createProductWithOption(1L, "상품1", 30000L, 101L);

        Product product2 = createProductWithOption(2L, "상품2", 70000L, 201L);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderItems(List.of(
                        OrderItemRequest.builder()
                                .productId(1L)
                                .optionId(101L)
                                .quantity(1)
                                .build(),
                        OrderItemRequest.builder()
                                .productId(2L)
                                .optionId(201L)
                                .quantity(1)
                                .build()
                ))
                .couponId(null)
                .build();

        Order savedOrder = Order.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus("PENDING")
                .subtotal(100000L)
                .couponDiscount(0L)
                .finalAmount(100000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product1));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product2));
        when(orderTransactionService.executeTransactionalOrder(
                anyLong(), anyList(), anyLong(), anyLong(), anyLong(), anyLong()
        )).thenReturn(savedOrder);

        // When
        CreateOrderResponse result = orderService.createOrder(TEST_USER_ID, request);

        // Then
        assertEquals(100000L, result.getSubtotal());
        assertEquals(100000L, result.getFinalAmount());
    }

    // ========== 트랜잭션 처리 검증 ==========

    @Test
    @DisplayName("주문 생성 - 트랜잭션 서비스 위임 검증")
    void testCreateOrder_TransactionServiceDelegation() {
        // Given
        User user = User.builder()
                .userId(TEST_USER_ID)
                .name("testuser")
                .email("test@example.com")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Product product = createProductWithOption(TEST_PRODUCT_ID, "상품1", 100000L);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderItems(List.of(
                        OrderItemRequest.builder()
                                .productId(TEST_PRODUCT_ID)
                                .optionId(TEST_OPTION_ID)
                                .quantity(1)
                                .build()
                ))
                .couponId(null)
                .build();

        Order savedOrder = Order.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus("PENDING")
                .subtotal(100000L)
                .couponDiscount(0L)
                .finalAmount(100000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
        when(orderTransactionService.executeTransactionalOrder(
                anyLong(), anyList(), anyLong(), anyLong(), anyLong(), anyLong()
        )).thenReturn(savedOrder);

        // When
        CreateOrderResponse result = orderService.createOrder(TEST_USER_ID, request);

        // Then
        // Verify that OrderTransactionService.executeTransactionalOrder was called exactly once
        verify(orderTransactionService, times(1)).executeTransactionalOrder(
                eq(TEST_USER_ID), anyList(), anyLong(), anyLong(), anyLong(), anyLong()
        );

        // Verify the result contains the saved order data
        assertNotNull(result);
        assertEquals(TEST_ORDER_ID, result.getOrderId());
    }

    // ========== Helper Methods ==========

    private Product createProductWithOption(Long productId, String productName, Long price) {
        return createProductWithOption(productId, productName, price, TEST_OPTION_ID);
    }

    private Product createProductWithOption(Long productId, String productName, Long price, Long optionId) {
        ProductOption option = ProductOption.builder()
                .optionId(optionId)
                .productId(productId)
                .name("기본옵션")
                .stock(10)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return Product.builder()
                .productId(productId)
                .productName(productName)
                .description(productName + " 설명")
                .price(price)
                .options(List.of(option))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
