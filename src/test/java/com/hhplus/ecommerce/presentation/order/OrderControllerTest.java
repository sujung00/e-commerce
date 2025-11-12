package com.hhplus.ecommerce.presentation.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.application.order.OrderService;
import com.hhplus.ecommerce.presentation.order.OrderController;
import com.hhplus.ecommerce.presentation.order.request.CreateOrderRequest;
import com.hhplus.ecommerce.presentation.order.request.OrderItemRequest;
import com.hhplus.ecommerce.presentation.order.response.CreateOrderResponse;
import com.hhplus.ecommerce.presentation.order.response.OrderDetailResponse;
import com.hhplus.ecommerce.presentation.order.response.OrderItemResponse;
import com.hhplus.ecommerce.presentation.order.response.OrderListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.hhplus.ecommerce.presentation.order.response.CancelOrderResponse;
import com.hhplus.ecommerce.domain.order.OrderNotFoundException;
import com.hhplus.ecommerce.domain.order.UserMismatchException;
import com.hhplus.ecommerce.domain.order.InvalidOrderStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OrderControllerTest - Presentation Layer Unit Test
 * Spring Boot 3.4+ Mockito 방식 테스트
 *
 * 테스트 대상: OrderController
 * - POST /orders - 주문 생성
 * - GET /orders/{order_id} - 주문 상세 조회
 * - GET /orders - 주문 목록 조회 (페이지네이션, 상태 필터)
 *
 * 테스트 유형:
 * - 성공 케이스: 정상적인 주문 생성, 조회
 * - 경계값 테스트: 페이지네이션, 상태 필터링
 * - 실패 케이스: 유효하지 않은 파라미터, 헤더
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController 단위 테스트")
@Disabled("MockMvc 라우팅 이슈 - @SpringBootTest 기반 통합 테스트(OrderControllerIntegrationTest)로 대체됨")
class OrderControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_ORDER_ID = 100L;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        this.mockMvc = MockMvcBuilders.standaloneSetup(orderController).build();
        this.objectMapper = new ObjectMapper();
    }

    // ========== 주문 생성 (POST /orders) ==========

    @Test
    @DisplayName("주문 생성 - 성공")
    void testCreateOrder_Success() throws Exception {
        // Given
        CreateOrderResponse response = CreateOrderResponse.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus("PENDING")
                .subtotal(100000L)
                .couponDiscount(5000L)
                .couponId(1L)
                .finalAmount(95000L)
                .orderItems(Arrays.asList(
                        OrderItemResponse.builder()
                                .orderItemId(1L)
                                .productId(1L)
                                .productName("상품1")
                                .optionId(1L)
                                .optionName("사이즈 M")
                                .quantity(2)
                                .price(50000L)
                                .build()
                ))
                .createdAt(LocalDateTime.now())
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderItems(Arrays.asList(
                        OrderItemRequest.builder()
                                .productId(1L)
                                .optionId(1L)
                                .quantity(2)
                                .build()
                ))
                .couponId(1L)
                .build();

        when(orderService.createOrder(anyLong(), any()))
                .thenAnswer(invocation -> response);

        // When & Then
        mockMvc.perform(post("/orders")
                .header("X-USER-ID", TEST_USER_ID)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order_id").value(TEST_ORDER_ID))
                .andExpect(jsonPath("$.order_status").value("PENDING"))
                .andExpect(jsonPath("$.subtotal").value(100000L))
                .andExpect(jsonPath("$.final_amount").value(95000L));

        verify(orderService, times(1)).createOrder(anyLong(), any());
    }

    @Test
    @DisplayName("주문 생성 - 성공 (쿠폰 미적용)")
    void testCreateOrder_Success_NoCoupon() throws Exception {
        // Given
        CreateOrderResponse response = CreateOrderResponse.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus("PENDING")
                .subtotal(50000L)
                .couponDiscount(0L)
                .couponId(null)
                .finalAmount(50000L)
                .orderItems(Collections.singletonList(
                        OrderItemResponse.builder()
                                .orderItemId(1L)
                                .productId(2L)
                                .productName("상품2")
                                .optionId(2L)
                                .optionName("색상 빨강")
                                .quantity(1)
                                .price(50000L)
                                .build()
                ))
                .createdAt(LocalDateTime.now())
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderItems(Arrays.asList(
                        OrderItemRequest.builder()
                                .productId(2L)
                                .optionId(2L)
                                .quantity(1)
                                .build()
                ))
                .couponId(null)
                .build();

        when(orderService.createOrder(anyLong(), any()))
                .thenAnswer(invocation -> response);

        // When & Then
        mockMvc.perform(post("/orders")
                .header("X-USER-ID", TEST_USER_ID)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coupon_discount").value(0L));

        verify(orderService, times(1)).createOrder(anyLong(), any());
    }

    @Test
    @DisplayName("주문 생성 - 실패 (헤더 누락)")
    void testCreateOrder_Failed_MissingHeader() throws Exception {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderItems(Arrays.asList(
                        OrderItemRequest.builder()
                                .productId(1L)
                                .optionId(1L)
                                .quantity(1)
                                .build()
                ))
                .couponId(null)
                .build();

        // When & Then
        mockMvc.perform(post("/orders")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주문 생성 - 실패 (요청 본문 누락)")
    void testCreateOrder_Failed_MissingBody() throws Exception {
        // When & Then
        mockMvc.perform(post("/orders")
                .header("X-USER-ID", TEST_USER_ID)
                .contentType("application/json"))
                .andExpect(status().isBadRequest());
    }

    // ========== 주문 상세 조회 (GET /orders/{order_id}) ==========

    @Test
    @DisplayName("주문 상세 조회 - 성공")
    void testGetOrderDetail_Success() throws Exception {
        // Given
        OrderDetailResponse response = OrderDetailResponse.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus("CONFIRMED")
                .subtotal(150000L)
                .couponDiscount(10000L)
                .couponId(1L)
                .finalAmount(140000L)
                .orderItems(Arrays.asList(
                        OrderItemResponse.builder()
                                .orderItemId(1L)
                                .productId(1L)
                                .productName("상품1")
                                .optionId(1L)
                                .optionName("사이즈 L")
                                .quantity(1)
                                .price(80000L)
                                .build(),
                        OrderItemResponse.builder()
                                .orderItemId(2L)
                                .productId(2L)
                                .productName("상품2")
                                .optionId(2L)
                                .optionName("색상 파랑")
                                .quantity(1)
                                .price(70000L)
                                .build()
                ))
                .createdAt(LocalDateTime.now().minusHours(2))
                .build();

        when(orderService.getOrderDetail(TEST_USER_ID, TEST_ORDER_ID))
                .thenAnswer(invocation -> response);

        // When & Then
        mockMvc.perform(get("/orders/{order_id}", TEST_ORDER_ID)
                .header("X-USER-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_id").value(TEST_ORDER_ID))
                .andExpect(jsonPath("$.order_status").value("CONFIRMED"))
                .andExpect(jsonPath("$.order_items").isArray())
                .andExpect(jsonPath("$.order_items", hasSize(2)));

        verify(orderService, times(1)).getOrderDetail(TEST_USER_ID, TEST_ORDER_ID);
    }

    @Test
    @DisplayName("주문 상세 조회 - 성공 (단일 주문 항목)")
    void testGetOrderDetail_Success_SingleItem() throws Exception {
        // Given
        OrderDetailResponse response = OrderDetailResponse.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus("PENDING")
                .subtotal(30000L)
                .couponDiscount(0L)
                .couponId(null)
                .finalAmount(30000L)
                .orderItems(Collections.singletonList(
                        OrderItemResponse.builder()
                                .orderItemId(1L)
                                .productId(3L)
                                .productName("상품3")
                                .optionId(3L)
                                .optionName("기본")
                                .quantity(1)
                                .price(30000L)
                                .build()
                ))
                .createdAt(LocalDateTime.now())
                .build();

        when(orderService.getOrderDetail(TEST_USER_ID, TEST_ORDER_ID))
                .thenAnswer(invocation -> response);

        // When & Then
        mockMvc.perform(get("/orders/{order_id}", TEST_ORDER_ID)
                .header("X-USER-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_items", hasSize(1)));

        verify(orderService, times(1)).getOrderDetail(TEST_USER_ID, TEST_ORDER_ID);
    }

    @Test
    @DisplayName("주문 상세 조회 - 실패 (유효하지 않은 order_id)")
    void testGetOrderDetail_Failed_InvalidOrderId() throws Exception {
        // When & Then
        mockMvc.perform(get("/orders/{order_id}", "invalid")
                .header("X-USER-ID", TEST_USER_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주문 상세 조회 - 실패 (음수 order_id)")
    void testGetOrderDetail_Failed_NegativeOrderId() throws Exception {
        // When & Then
        mockMvc.perform(get("/orders/{order_id}", "-1")
                .header("X-USER-ID", TEST_USER_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주문 상세 조회 - 실패 (헤더 누락)")
    void testGetOrderDetail_Failed_MissingHeader() throws Exception {
        // When & Then
        mockMvc.perform(get("/orders/{order_id}", TEST_ORDER_ID))
                .andExpect(status().isBadRequest());
    }

    // ========== 주문 목록 조회 (GET /orders) ==========

    @Test
    @DisplayName("주문 목록 조회 - 성공 (기본 파라미터)")
    void testGetOrderList_Success_DefaultParameters() throws Exception {
        // Given
        OrderListResponse response = OrderListResponse.builder()
                .content(Arrays.asList(
                        OrderListResponse.OrderSummary.builder()
                                .orderId(100L)
                                .orderStatus("CONFIRMED")
                                .finalAmount(95000L)
                                .createdAt(LocalDateTime.now().minusDays(1))
                                .build(),
                        OrderListResponse.OrderSummary.builder()
                                .orderId(101L)
                                .orderStatus("PENDING")
                                .finalAmount(50000L)
                                .createdAt(LocalDateTime.now())
                                .build()
                ))
                .totalElements(50L)
                .totalPages(5)
                .currentPage(0)
                .size(10)
                .build();

        when(orderService.getOrderList(TEST_USER_ID, 0, 10, Optional.empty()))
                .thenAnswer(invocation -> response);

        // When & Then
        mockMvc.perform(get("/orders")
                .header("X-USER-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").isArray())
                .andExpect(jsonPath("$.orders", hasSize(2)))
                .andExpect(jsonPath("$.total_count").value(50L))
                .andExpect(jsonPath("$.current_page").value(0));

        verify(orderService, times(1)).getOrderList(TEST_USER_ID, 0, 10, Optional.empty());
    }

    @Test
    @DisplayName("주문 목록 조회 - 성공 (커스텀 페이지네이션)")
    void testGetOrderList_Success_CustomPagination() throws Exception {
        // Given
        OrderListResponse response = OrderListResponse.builder()
                .content(Collections.singletonList(
                        OrderListResponse.OrderSummary.builder()
                                .orderId(110L)
                                .orderStatus("SHIPPED")
                                .finalAmount(75000L)
                                .createdAt(LocalDateTime.now().minusDays(5))
                                .build()
                ))
                .totalElements(50L)
                .totalPages(5)
                .currentPage(1)
                .size(10)
                .build();

        when(orderService.getOrderList(TEST_USER_ID, 1, 10, Optional.empty()))
                .thenAnswer(invocation -> response);

        // When & Then
        mockMvc.perform(get("/orders")
                .header("X-USER-ID", TEST_USER_ID)
                .param("page", "1")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_page").value(1))
                .andExpect(jsonPath("$.page_size").value(10));

        verify(orderService, times(1)).getOrderList(TEST_USER_ID, 1, 10, Optional.empty());
    }

    @Test
    @DisplayName("주문 목록 조회 - 성공 (상태 필터링: CONFIRMED)")
    void testGetOrderList_Success_StatusFilter() throws Exception {
        // Given
        OrderListResponse response = OrderListResponse.builder()
                .content(Collections.singletonList(
                        OrderListResponse.OrderSummary.builder()
                                .orderId(100L)
                                .orderStatus("CONFIRMED")
                                .finalAmount(95000L)
                                .createdAt(LocalDateTime.now().minusDays(1))
                                .build()
                ))
                .totalElements(10L)
                .totalPages(1)
                .currentPage(0)
                .size(10)
                .build();

        when(orderService.getOrderList(TEST_USER_ID, 0, 10, Optional.of("CONFIRMED")))
                .thenAnswer(invocation -> response);

        // When & Then
        mockMvc.perform(get("/orders")
                .header("X-USER-ID", TEST_USER_ID)
                .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[0].order_status").value("CONFIRMED"));

        verify(orderService, times(1)).getOrderList(TEST_USER_ID, 0, 10, Optional.of("CONFIRMED"));
    }

    @Test
    @DisplayName("주문 목록 조회 - 성공 (빈 결과)")
    void testGetOrderList_Success_EmptyResult() throws Exception {
        // Given
        OrderListResponse response = OrderListResponse.builder()
                .content(Collections.emptyList())
                .totalElements(0L)
                .totalPages(0)
                .currentPage(0)
                .size(10)
                .build();

        when(orderService.getOrderList(TEST_USER_ID, 0, 10, Optional.empty()))
                .thenAnswer(invocation -> response);

        // When & Then
        mockMvc.perform(get("/orders")
                .header("X-USER-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders", hasSize(0)))
                .andExpect(jsonPath("$.total_count").value(0L));

        verify(orderService, times(1)).getOrderList(TEST_USER_ID, 0, 10, Optional.empty());
    }

    @Test
    @DisplayName("주문 목록 조회 - 실패 (유효하지 않은 page)")
    void testGetOrderList_Failed_InvalidPage() throws Exception {
        // When & Then
        mockMvc.perform(get("/orders")
                .header("X-USER-ID", TEST_USER_ID)
                .param("page", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주문 목록 조회 - 실패 (음수 page)")
    void testGetOrderList_Failed_NegativePage() throws Exception {
        // When & Then
        mockMvc.perform(get("/orders")
                .header("X-USER-ID", TEST_USER_ID)
                .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주문 목록 조회 - 실패 (헤더 누락)")
    void testGetOrderList_Failed_MissingHeader() throws Exception {
        // When & Then
        mockMvc.perform(get("/orders"))
                .andExpect(status().isBadRequest());
    }

    // ========== 응답 포맷 검증 ==========

    @Test
    @DisplayName("주문 생성 - 응답 필드 검증")
    void testCreateOrder_ResponseFieldValidation() throws Exception {
        // Given
        CreateOrderResponse response = CreateOrderResponse.builder()
                .orderId(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .orderStatus("PENDING")
                .subtotal(100000L)
                .couponDiscount(0L)
                .finalAmount(100000L)
                .orderItems(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderItems(Arrays.asList(
                        OrderItemRequest.builder()
                                .productId(1L)
                                .optionId(1L)
                                .quantity(1)
                                .build()
                ))
                .couponId(null)
                .build();

        when(orderService.createOrder(anyLong(), any()))
                .thenAnswer(invocation -> response);

        // When & Then
        mockMvc.perform(post("/orders")
                .header("X-USER-ID", TEST_USER_ID)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order_id").isNumber())
                .andExpect(jsonPath("$.order_status").isString())
                .andExpect(jsonPath("$.subtotal").isNumber())
                .andExpect(jsonPath("$.final_amount").isNumber())
                .andExpect(jsonPath("$.order_items").isArray());
    }

    @Test
    @DisplayName("주문 목록 조회 - 응답 필드 검증")
    void testGetOrderList_ResponseFieldValidation() throws Exception {
        // Given
        when(orderService.getOrderList(anyLong(), anyInt(), anyInt(), any(Optional.class)))
                .thenAnswer(invocation -> OrderListResponse.builder()
                        .content(Collections.singletonList(
                                OrderListResponse.OrderSummary.builder()
                                        .orderId(100L)
                                        .orderStatus("PENDING")
                                        .finalAmount(100000L)
                                        .createdAt(LocalDateTime.now())
                                        .build()
                        ))
                        .totalElements(1L)
                        .totalPages(1)
                        .currentPage(0)
                        .size(10)
                        .build());

        // When & Then
        mockMvc.perform(get("/orders")
                .header("X-USER-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").isArray())
                .andExpect(jsonPath("$.total_count").isNumber())
                .andExpect(jsonPath("$.total_page").isNumber())
                .andExpect(jsonPath("$.current_page").isNumber())
                .andExpect(jsonPath("$.page_size").isNumber());
    }

    // ========== 주문 취소 (3.4 API) ==========

    @Test
    @DisplayName("주문 취소 - 성공 (200 OK)")
    void testCancelOrder_Success() throws Exception {
        // Given
        Long orderId = 100L;
        CancelOrderResponse response = CancelOrderResponse.builder()
                .orderId(orderId)
                .orderStatus("CANCELLED")
                .refundAmount(95000L)
                .cancelledAt(Instant.now())
                .restoredItems(Collections.singletonList(
                        CancelOrderResponse.RestoredItem.builder()
                                .orderItemId(1L)
                                .productId(1L)
                                .productName("상품1")
                                .optionId(101L)
                                .optionName("기본옵션")
                                .quantity(2)
                                .restoredStock(10)
                                .build()
                ))
                .build();

        when(orderService.cancelOrder(TEST_USER_ID, orderId)).thenAnswer(invocation -> response);

        // When & Then
        mockMvc.perform(post("/orders/{order_id}/cancel", orderId)
                .header("X-USER-ID", TEST_USER_ID)
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_id").value(orderId))
                .andExpect(jsonPath("$.order_status").value("CANCELLED"))
                .andExpect(jsonPath("$.subtotal").value(100000L))
                .andExpect(jsonPath("$.coupon_discount").value(5000L))
                .andExpect(jsonPath("$.final_amount").value(95000L))
                .andExpect(jsonPath("$.restored_amount").value(95000L))
                .andExpect(jsonPath("$.cancelled_at").isNotEmpty())
                .andExpect(jsonPath("$.restored_items").isArray())
                .andExpect(jsonPath("$.restored_items[0].order_item_id").value(1L))
                .andExpect(jsonPath("$.restored_items[0].product_id").value(1L))
                .andExpect(jsonPath("$.restored_items[0].product_name").value("상품1"))
                .andExpect(jsonPath("$.restored_items[0].option_id").value(101L))
                .andExpect(jsonPath("$.restored_items[0].option_name").value("기본옵션"))
                .andExpect(jsonPath("$.restored_items[0].quantity").value(2))
                .andExpect(jsonPath("$.restored_items[0].restored_stock").value(10));

        verify(orderService, times(1)).cancelOrder(TEST_USER_ID, orderId);
    }

    @Test
    @DisplayName("주문 취소 - 실패 (404 Not Found - ORDER_NOT_FOUND)")
    void testCancelOrder_Failed_OrderNotFound() throws Exception {
        // Given
        Long orderId = 999L;
        when(orderService.cancelOrder(TEST_USER_ID, orderId))
                .thenThrow(new OrderNotFoundException(orderId));

        // When & Then
        mockMvc.perform(post("/orders/{order_id}/cancel", orderId)
                .header("X-USER-ID", TEST_USER_ID)
                .contentType("application/json"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.error_message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.request_id").isNotEmpty());

        verify(orderService, times(1)).cancelOrder(TEST_USER_ID, orderId);
    }

    @Test
    @DisplayName("주문 취소 - 실패 (404 Not Found - USER_MISMATCH)")
    void testCancelOrder_Failed_UserMismatch() throws Exception {
        // Given
        Long orderId = 100L;
        Long otherUserId = 999L;
        when(orderService.cancelOrder(otherUserId, orderId))
                .thenThrow(new UserMismatchException(orderId, otherUserId));

        // When & Then
        mockMvc.perform(post("/orders/{order_id}/cancel", orderId)
                .header("X-USER-ID", otherUserId)
                .contentType("application/json"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("USER_MISMATCH"))
                .andExpect(jsonPath("$.error_message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.request_id").isNotEmpty());

        verify(orderService, times(1)).cancelOrder(otherUserId, orderId);
    }

    @Test
    @DisplayName("주문 취소 - 실패 (400 Bad Request - INVALID_ORDER_STATUS)")
    void testCancelOrder_Failed_InvalidOrderStatus() throws Exception {
        // Given
        Long orderId = 100L;
        when(orderService.cancelOrder(TEST_USER_ID, orderId))
                .thenThrow(new InvalidOrderStatusException("주문 상태가 COMPLETED가 아니므로 취소할 수 없습니다"));

        // When & Then
        mockMvc.perform(post("/orders/{order_id}/cancel", orderId)
                .header("X-USER-ID", TEST_USER_ID)
                .contentType("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_ORDER_STATUS"))
                .andExpect(jsonPath("$.error_message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.request_id").isNotEmpty());

        verify(orderService, times(1)).cancelOrder(TEST_USER_ID, orderId);
    }

    @Test
    @DisplayName("주문 취소 - 성공 (쿠폰 미적용)")
    void testCancelOrder_Success_NoCoupon() throws Exception {
        // Given
        Long orderId = 100L;
        CancelOrderResponse response = CancelOrderResponse.builder()
                .orderId(orderId)
                .orderStatus("CANCELLED")
                .refundAmount(50000L)
                .cancelledAt(Instant.now())
                .restoredItems(Collections.singletonList(
                        CancelOrderResponse.RestoredItem.builder()
                                .orderItemId(1L)
                                .productId(1L)
                                .productName("상품1")
                                .optionId(101L)
                                .optionName("기본옵션")
                                .quantity(1)
                                .restoredStock(11)
                                .build()
                ))
                .build();

        when(orderService.cancelOrder(TEST_USER_ID, orderId)).thenAnswer(invocation -> response);

        // When & Then
        mockMvc.perform(post("/orders/{order_id}/cancel", orderId)
                .header("X-USER-ID", TEST_USER_ID)
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupon_discount").value(0L))
                .andExpect(jsonPath("$.final_amount").value(50000L))
                .andExpect(jsonPath("$.restored_amount").value(50000L));

        verify(orderService, times(1)).cancelOrder(TEST_USER_ID, orderId);
    }

    @Test
    @DisplayName("주문 취소 - 다중 항목 복구 검증")
    void testCancelOrder_Success_MultipleItems() throws Exception {
        // Given
        Long orderId = 100L;
        CancelOrderResponse response = CancelOrderResponse.builder()
                .orderId(orderId)
                .orderStatus("CANCELLED")
                .refundAmount(150000L)
                .cancelledAt(Instant.now())
                .restoredItems(Arrays.asList(
                        CancelOrderResponse.RestoredItem.builder()
                                .orderItemId(1L)
                                .productId(1L)
                                .productName("상품1")
                                .optionId(101L)
                                .optionName("옵션A")
                                .quantity(2)
                                .restoredStock(10)
                                .build(),
                        CancelOrderResponse.RestoredItem.builder()
                                .orderItemId(2L)
                                .productId(2L)
                                .productName("상품2")
                                .optionId(102L)
                                .optionName("옵션B")
                                .quantity(3)
                                .restoredStock(8)
                                .build()
                ))
                .build();

        when(orderService.cancelOrder(TEST_USER_ID, orderId)).thenAnswer(invocation -> response);

        // When & Then
        mockMvc.perform(post("/orders/{order_id}/cancel", orderId)
                .header("X-USER-ID", TEST_USER_ID)
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restored_items", hasSize(2)))
                .andExpect(jsonPath("$.restored_items[0].order_item_id").value(1L))
                .andExpect(jsonPath("$.restored_items[0].product_id").value(1L))
                .andExpect(jsonPath("$.restored_items[0].quantity").value(2))
                .andExpect(jsonPath("$.restored_items[1].order_item_id").value(2L))
                .andExpect(jsonPath("$.restored_items[1].product_id").value(2L))
                .andExpect(jsonPath("$.restored_items[1].quantity").value(3));

        verify(orderService, times(1)).cancelOrder(TEST_USER_ID, orderId);
    }

    @Test
    @DisplayName("주문 취소 - 요청/응답 필드 검증")
    void testCancelOrder_RequestResponseValidation() throws Exception {
        // Given
        Long orderId = 100L;
        CancelOrderResponse response = CancelOrderResponse.builder()
                .orderId(orderId)
                .orderStatus("CANCELLED")
                .refundAmount(100000L)
                .cancelledAt(Instant.now())
                .restoredItems(new ArrayList<>())
                .build();

        when(orderService.cancelOrder(TEST_USER_ID, orderId)).thenAnswer(invocation -> response);

        // When & Then - response fields should use snake_case
        mockMvc.perform(post("/orders/{order_id}/cancel", orderId)
                .header("X-USER-ID", TEST_USER_ID)
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_id").exists())
                .andExpect(jsonPath("$.order_status").exists())
                .andExpect(jsonPath("$.subtotal").exists())
                .andExpect(jsonPath("$.coupon_discount").exists())
                .andExpect(jsonPath("$.final_amount").exists())
                .andExpect(jsonPath("$.restored_amount").exists())
                .andExpect(jsonPath("$.cancelled_at").exists())
                .andExpect(jsonPath("$.restored_items").exists());

        verify(orderService, times(1)).cancelOrder(TEST_USER_ID, orderId);
    }
}
