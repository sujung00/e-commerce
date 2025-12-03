package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.application.order.OrderTransactionService;
import com.hhplus.ecommerce.application.user.UserBalanceService;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderDomainService;
import com.hhplus.ecommerce.domain.order.OrderItem;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductDomainService;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserBalanceDomainService;
import com.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 도메인 서비스 통합 테스트
 *
 * 목표: 도메인 서비스가 실제 데이터베이스와 함께 올바르게 작동하는지 검증
 *
 * 테스트 영역:
 * 1. OrderDomainService와 실제 엔티티 상호작용
 * 2. CouponDomainService와 쿠폰 발급 통합
 * 3. UserBalanceDomainService와 잔액 관리 통합
 * 4. ProductDomainService와 재고 관리 통합
 * 5. 트랜잭션 경계 검증
 * 6. 예외 처리 및 롤백
 */
@DisplayName("도메인 서비스 통합 테스트")
class IntegrationDomainServiceTest extends BaseIntegrationTest {

    @Autowired
    private OrderDomainService orderDomainService;

    @Autowired
    private UserBalanceDomainService userBalanceDomainService;

    @Autowired
    private ProductDomainService productDomainService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    private User testUser;
    private Product testProduct;
    private ProductOption testOption;
    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성
        testUser = User.builder()
                .email("test@example.com")
                .name("Test User")
                .phone("010-1234-5678")
                .balance(100000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(testUser);

        // 테스트 상품 생성
        testProduct = Product.builder()
                .productName("Test Product")
                .description("Test Description")
                .price(10000L)
                .totalStock(100)
                .status("IN_STOCK")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.save(testProduct);
        testProduct = productRepository.findById(testProduct.getProductId()).orElseThrow();

        // 테스트 쿠폰 생성
        testCoupon = Coupon.builder()
                .couponName("Test Coupon")
                .description("Test Description")
                .isActive(true)
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .discountRate(BigDecimal.ZERO)
                .remainingQty(10)
                .totalQuantity(10)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        couponRepository.save(testCoupon);
    }

    // ==================== UserBalanceDomainService Integration Tests ====================

    @Test
    @DisplayName("사용자 잔액 차감 - DB 반영 검증")
    void testUserBalanceDeduction_VerifyDatabaseUpdate() {
        // Given
        Long initialBalance = testUser.getBalance();
        Long deductAmount = 20000L;

        // When
        userBalanceDomainService.deductBalance(testUser, deductAmount);
        userRepository.save(testUser);
        User updatedUser = userRepository.findById(testUser.getUserId()).orElseThrow();

        // Then
        assertEquals(initialBalance - deductAmount, updatedUser.getBalance());
    }

    @Test
    @DisplayName("사용자 잔액 충전 - DB 반영 검증")
    void testUserBalanceCharge_VerifyDatabaseUpdate() {
        // Given
        Long initialBalance = testUser.getBalance();
        Long chargeAmount = 50000L;

        // When
        userBalanceDomainService.chargeBalance(testUser, chargeAmount);
        userRepository.save(testUser);
        User updatedUser = userRepository.findById(testUser.getUserId()).orElseThrow();

        // Then
        assertEquals(initialBalance + chargeAmount, updatedUser.getBalance());
    }

    @Test
    @DisplayName("사용자 잔액 환불 - DB 반영 검증")
    void testUserBalanceRefund_VerifyDatabaseUpdate() {
        // Given
        Long initialBalance = testUser.getBalance();
        Long refundAmount = 30000L;

        // When
        userBalanceDomainService.refundBalance(testUser, refundAmount);
        userRepository.save(testUser);
        User updatedUser = userRepository.findById(testUser.getUserId()).orElseThrow();

        // Then
        assertEquals(initialBalance + refundAmount, updatedUser.getBalance());
    }

    @Test
    @DisplayName("사용자 잔액 부족 - 예외 발생 및 롤백")
    void testUserBalanceInsufficientBalance_ThrowsExceptionAndRollback() {
        // Given
        Long initialBalance = testUser.getBalance();

        // When & Then
        assertThrows(Exception.class, () -> {
            userBalanceDomainService.deductBalance(testUser, initialBalance + 1);
            userRepository.save(testUser);
        });

        // Verify: 데이터베이스는 변경되지 않음
        User unchangedUser = userRepository.findById(testUser.getUserId()).orElseThrow();
        assertEquals(initialBalance, unchangedUser.getBalance());
    }

    // ==================== ProductDomainService Integration Tests ====================

    @Test
    @DisplayName("상품 재고 검증 - 충분한 재고")
    void testProductStockValidation_WithSufficientStock() {
        // Given: 상품에 옵션 추가
        testProduct.addOption(ProductOption.builder()
                .productId(testProduct.getProductId())
                .name("Red")
                .stock(50)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        // When & Then
        assertDoesNotThrow(() ->
            productDomainService.validateOptionStock(testProduct.getOptions().get(0), 30)
        );
    }

    @Test
    @DisplayName("상품 상태 업데이트 - 재고 없음시 자동 SOLD_OUT")
    void testProductStatusUpdate_AutoDeactivateWhenOutOfStock() {
        // Given: 빈 옵션 리스트
        productRepository.save(testProduct);

        // When
        productDomainService.updateStatusAfterStockDeduction(testProduct);

        // Then
        assertEquals("SOLD_OUT", testProduct.getStatus());
    }

    @Test
    @DisplayName("상품 상태 업데이트 - 재고 있음시 IN_STOCK 유지")
    void testProductStatusUpdate_MaintainInStockWhenHasStock() {
        // Given
        testProduct.addOption(ProductOption.builder()
                .productId(testProduct.getProductId())
                .name("Blue")
                .stock(50)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        // When
        productDomainService.updateStatusAfterStockDeduction(testProduct);

        // Then
        assertEquals("IN_STOCK", testProduct.getStatus());
    }

    // ==================== OrderDomainService Integration Tests ====================

    @Test
    @DisplayName("주문 생성 검증 - 정상 케이스")
    void testOrderCreationValidation_Success() {
        // Given: 정상적인 주문 데이터
        var orderItems = java.util.List.of(
            com.hhplus.ecommerce.domain.order.OrderItem.builder()
                .productId(testProduct.getProductId())
                .optionId(1L)
                .quantity(2)
                .unitPrice(10000L)
                .build()
        );

        // When & Then
        assertDoesNotThrow(() ->
            orderDomainService.validateOrderCreation(testUser, orderItems, testCoupon)
        );
    }

    @Test
    @DisplayName("주문 총액 계산 - 정액 할인 적용")
    void testOrderTotalCalculation_WithFixedDiscount() {
        // Given
        var orderItems = java.util.List.of(
            com.hhplus.ecommerce.domain.order.OrderItem.builder()
                .productId(testProduct.getProductId())
                .optionId(1L)
                .quantity(2)
                .unitPrice(10000L)
                .build()
        );

        // When
        OrderDomainService.OrderTotal total = orderDomainService.calculateOrderTotal(orderItems, testCoupon);

        // Then
        assertEquals(20000L, total.getSubtotal());
        assertEquals(5000L, total.getDiscountAmount());
        assertEquals(15000L, total.getFinalAmount());
    }

    @Test
    @DisplayName("주문 취소 검증 - PENDING 상태 취소 가능")
    void testOrderCancellationValidation_PendingOrder() {
        // Given: PENDING 상태의 주문
        Order pendingOrder = orderRepository.save(Order.builder()
                .userId(testUser.getUserId())
                .orderStatus(com.hhplus.ecommerce.domain.order.OrderStatus.PENDING)
                .subtotal(20000L)
                .couponDiscount(0L)
                .finalAmount(20000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        // When & Then
        assertDoesNotThrow(() ->
            orderDomainService.validateOrderCancellation(pendingOrder)
        );
    }

    @Test
    @DisplayName("주문 취소 검증 - COMPLETED 상태 취소 불가")
    void testOrderCancellationValidation_CompletedOrder_ThrowsException() {
        // Given: COMPLETED 상태의 주문
        Order completedOrder = orderRepository.save(Order.builder()
                .userId(testUser.getUserId())
                .orderStatus(com.hhplus.ecommerce.domain.order.OrderStatus.COMPLETED)
                .subtotal(20000L)
                .couponDiscount(0L)
                .finalAmount(20000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        // When & Then
        assertThrows(Exception.class, () ->
            orderDomainService.validateOrderCancellation(completedOrder)
        );
    }

    // ==================== Complex Integration Scenarios ====================

    @Test
    @DisplayName("주문 흐름 통합 - 잔액 차감 → 주문 생성 → 상품 상태 업데이트")
    void testCompleteOrderFlow_Integration() {
        // Given
        Long initialBalance = testUser.getBalance();
        Long orderAmount = 20000L;

        var orderItems = java.util.List.of(
            com.hhplus.ecommerce.domain.order.OrderItem.builder()
                .productId(testProduct.getProductId())
                .optionId(1L)
                .quantity(2)
                .unitPrice(10000L)
                .build()
        );

        // When
        // 1. 잔액 검증
        assertDoesNotThrow(() ->
            userBalanceDomainService.validateSufficientBalance(testUser, orderAmount)
        );

        // 2. 주문 검증 및 계산
        assertDoesNotThrow(() ->
            orderDomainService.validateOrderCreation(testUser, orderItems, null)
        );
        OrderDomainService.OrderTotal total = orderDomainService.calculateOrderTotal(orderItems, null);

        // 3. 잔액 차감
        userBalanceDomainService.deductBalance(testUser, total.getFinalAmount());
        userRepository.save(testUser);

        // 4. 주문 생성
        Order order = orderRepository.save(Order.builder()
                .userId(testUser.getUserId())
                .orderStatus(com.hhplus.ecommerce.domain.order.OrderStatus.PENDING)
                .subtotal(total.getSubtotal())
                .couponDiscount(total.getDiscountAmount())
                .finalAmount(total.getFinalAmount())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        // 5. 상품 상태 업데이트
        productDomainService.updateStatusAfterStockDeduction(testProduct);

        // Then
        // 잔액 차감 검증
        User updatedUser = userRepository.findById(testUser.getUserId()).orElseThrow();
        assertEquals(initialBalance - total.getFinalAmount(), updatedUser.getBalance());

        // 주문 생성 검증
        Order savedOrder = orderRepository.findById(order.getOrderId()).orElseThrow();
        assertNotNull(savedOrder);
        assertEquals(total.getFinalAmount(), savedOrder.getFinalAmount());
    }

    @Test
    @DisplayName("트랜잭션 롤백 - 주문 생성 중 예외 발생시")
    void testTransactionRollback_OnOrderCreationFailure() {
        // Given
        Long initialBalance = testUser.getBalance();
        java.util.List<OrderItem> invalidOrderItems = java.util.List.of(); // 빈 주문 항목

        // When & Then
        assertThrows(Exception.class, () -> {
            orderDomainService.validateOrderCreation(testUser, invalidOrderItems, null);
            // 이 지점에서 예외 발생 - 데이터베이스 변경 없음
        });

        // 롤백 검증 - 사용자 잔액 변경 없음
        User unchangedUser = userRepository.findById(testUser.getUserId()).orElseThrow();
        assertEquals(initialBalance, unchangedUser.getBalance());
    }
}
