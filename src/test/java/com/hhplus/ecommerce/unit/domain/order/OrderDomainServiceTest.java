package com.hhplus.ecommerce.unit.domain.order;

import com.hhplus.ecommerce.common.exception.ErrorCode;
import com.hhplus.ecommerce.common.exception.DomainException;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderDomainService;
import com.hhplus.ecommerce.domain.order.OrderItem;
import com.hhplus.ecommerce.domain.order.OrderStatus;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OrderDomainService - 주문 도메인 비즈니스 로직")
class OrderDomainServiceTest {

    private OrderDomainService orderDomainService;

    @BeforeEach
    void setUp() {
        orderDomainService = new OrderDomainService();
    }

    // ==================== validateOrderCreation Tests ====================

    @Test
    @DisplayName("주문 생성 검증 - 정상 케이스")
    void validateOrderCreation_WithValidData_Success() {
        // Given
        User user = createTestUser(1L);
        List<OrderItem> orderItems = createTestOrderItems();

        // When & Then
        assertDoesNotThrow(() -> orderDomainService.validateOrderCreation(user, orderItems, null));
    }

    @Test
    @DisplayName("주문 생성 검증 - null 사용자")
    void validateOrderCreation_WithNullUser_ThrowsException() {
        // Given
        List<OrderItem> orderItems = createTestOrderItems();

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> orderDomainService.validateOrderCreation(null, orderItems, null));
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("주문 생성 검증 - 빈 주문 항목")
    void validateOrderCreation_WithEmptyOrderItems_ThrowsException() {
        // Given
        User user = createTestUser(1L);

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> orderDomainService.validateOrderCreation(user, new ArrayList<>(), null));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS, exception.getErrorCode());
    }

    @Test
    @DisplayName("주문 생성 검증 - 잘못된 수량")
    void validateOrderCreation_WithInvalidQuantity_ThrowsException() {
        // Given
        User user = createTestUser(1L);
        List<OrderItem> orderItems = new ArrayList<>();
        orderItems.add(OrderItem.builder()
                .quantity(0)
                .unitPrice(10000L)
                .productId(1L)
                .optionId(1L)
                .build());

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> orderDomainService.validateOrderCreation(user, orderItems, null));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS, exception.getErrorCode());
    }

    @Test
    @DisplayName("주문 생성 검증 - 잘못된 가격")
    void validateOrderCreation_WithInvalidPrice_ThrowsException() {
        // Given
        User user = createTestUser(1L);
        List<OrderItem> orderItems = new ArrayList<>();
        orderItems.add(OrderItem.builder()
                .quantity(1)
                .unitPrice(0L)
                .productId(1L)
                .optionId(1L)
                .build());

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> orderDomainService.validateOrderCreation(user, orderItems, null));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS, exception.getErrorCode());
    }

    @Test
    @DisplayName("주문 생성 검증 - 비활성 쿠폰")
    void validateOrderCreation_WithInactiveCoupon_ThrowsException() {
        // Given
        User user = createTestUser(1L);
        List<OrderItem> orderItems = createTestOrderItems();
        Coupon coupon = createTestCoupon(1L, false);

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> orderDomainService.validateOrderCreation(user, orderItems, coupon));
        assertEquals(ErrorCode.COUPON_INACTIVE, exception.getErrorCode());
    }

    // ==================== calculateOrderTotal Tests ====================

    @Test
    @DisplayName("주문 총액 계산 - 쿠폰 없음")
    void calculateOrderTotal_WithoutCoupon_ReturnsCorrectTotal() {
        // Given
        List<OrderItem> orderItems = createTestOrderItems(); // 단가 10000원 × 2개 = 20000원

        // When
        OrderDomainService.OrderTotal total = orderDomainService.calculateOrderTotal(orderItems, null);

        // Then
        assertEquals(20000, total.getSubtotal());
        assertEquals(0, total.getDiscountAmount());
        assertEquals(20000, total.getFinalAmount());
    }

    @Test
    @DisplayName("주문 총액 계산 - 정액 쿠폰")
    void calculateOrderTotal_WithFixedAmountCoupon_AppliesDiscount() {
        // Given
        List<OrderItem> orderItems = createTestOrderItems(); // 20000원
        Coupon coupon = createFixedAmountCoupon(1L, 5000L); // 5000원 할인

        // When
        OrderDomainService.OrderTotal total = orderDomainService.calculateOrderTotal(orderItems, coupon);

        // Then
        assertEquals(20000, total.getSubtotal());
        assertEquals(5000, total.getDiscountAmount());
        assertEquals(15000, total.getFinalAmount());
    }

    @Test
    @DisplayName("주문 총액 계산 - 비율 쿠폰")
    void calculateOrderTotal_WithPercentageCoupon_AppliesDiscount() {
        // Given
        List<OrderItem> orderItems = createTestOrderItems(); // 20000원
        Coupon coupon = createPercentageCoupon(1L, 0.1); // 10% 할인 = 2000원

        // When
        OrderDomainService.OrderTotal total = orderDomainService.calculateOrderTotal(orderItems, coupon);

        // Then
        assertEquals(20000, total.getSubtotal());
        assertEquals(2000, total.getDiscountAmount());
        assertEquals(18000, total.getFinalAmount());
    }

    @Test
    @DisplayName("주문 총액 계산 - 할인이 주문액을 초과하는 경우")
    void calculateOrderTotal_WithExcessiveDiscount_CapsDiscountAtSubtotal() {
        // Given
        List<OrderItem> orderItems = createTestOrderItems(); // 20000원
        Coupon coupon = createFixedAmountCoupon(1L, 50000L); // 50000원 할인 시도

        // When
        OrderDomainService.OrderTotal total = orderDomainService.calculateOrderTotal(orderItems, coupon);

        // Then
        assertEquals(20000, total.getSubtotal());
        assertEquals(20000, total.getDiscountAmount()); // 최대 주문액까지만
        assertEquals(0, total.getFinalAmount());
    }

    // ==================== validateOrderCancellation Tests ====================

    @Test
    @DisplayName("주문 취소 검증 - 정상 케이스")
    void validateOrderCancellation_WithCancellableOrder_Success() {
        // Given
        Order order = createTestOrder(1L, "PENDING");

        // When & Then
        assertDoesNotThrow(() -> orderDomainService.validateOrderCancellation(order));
    }

    @Test
    @DisplayName("주문 취소 검증 - null 주문")
    void validateOrderCancellation_WithNullOrder_ThrowsException() {
        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> orderDomainService.validateOrderCancellation(null));
        assertEquals(ErrorCode.ORDER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("주문 취소 검증 - 취소 불가능한 상태")
    void validateOrderCancellation_WithNonCancellableOrder_ThrowsException() {
        // Given
        Order order = createTestOrder(1L, "COMPLETED");

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> orderDomainService.validateOrderCancellation(order));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS, exception.getErrorCode());
    }

    // ==================== updateProductStatusAfterOrder Tests ====================

    @Test
    @DisplayName("주문 후 상품 상태 업데이트 - 성공")
    void updateProductStatusAfterOrder_WithValidData_UpdatesStatus() {
        // Given
        Order order = createTestOrder(1L, "PENDING");
        Product product = createTestProduct(1L);
        ProductOption option = createTestProductOption(1L, 10);
        product.addOption(option);
        List<Product> products = List.of(product);

        int initialStock = product.getTotalStock();

        // When
        orderDomainService.updateProductStatusAfterOrder(order, products);

        // Then
        assertEquals(initialStock, product.getTotalStock()); // 상태 업데이트만, 재고는 변경 안함
        assertTrue("IN_STOCK".equals(product.getStatus()) || "SOLD_OUT".equals(product.getStatus()));
    }

    @Test
    @DisplayName("주문 후 상품 상태 업데이트 - null 주문")
    void updateProductStatusAfterOrder_WithNullOrder_DoesNothing() {
        // Given
        List<Product> products = List.of(createTestProduct(1L));

        // When & Then
        assertDoesNotThrow(() -> orderDomainService.updateProductStatusAfterOrder(null, products));
    }

    // ==================== Helper Methods ====================

    private User createTestUser(Long userId) {
        return User.builder()
                .userId(userId)
                .email("test" + userId + "@example.com")
                .name("TestUser")
                .phone("010-1234-5678")
                .balance(100000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private List<OrderItem> createTestOrderItems() {
        List<OrderItem> items = new ArrayList<>();
        items.add(OrderItem.builder()
                .productId(1L)
                .optionId(1L)
                .quantity(2)
                .unitPrice(10000L)
                .build());
        return items;
    }

    private Coupon createTestCoupon(Long couponId, boolean isActive) {
        return Coupon.builder()
                .couponId(couponId)
                .couponName("Test Coupon")
                .isActive(isActive)
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .remainingQty(10)
                .totalQuantity(10)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Coupon createFixedAmountCoupon(Long couponId, Long discountAmount) {
        return Coupon.builder()
                .couponId(couponId)
                .couponName("Fixed Discount")
                .isActive(true)
                .discountType("FIXED_AMOUNT")
                .discountAmount(discountAmount)
                .discountRate(BigDecimal.ZERO)
                .remainingQty(10)
                .totalQuantity(10)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Coupon createPercentageCoupon(Long couponId, double discountRate) {
        return Coupon.builder()
                .couponId(couponId)
                .couponName("Percentage Discount")
                .isActive(true)
                .discountType("PERCENTAGE")
                .discountAmount(0L)
                .discountRate(new BigDecimal(discountRate))
                .remainingQty(10)
                .totalQuantity(10)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Order createTestOrder(Long orderId, String statusString) {
        OrderStatus status = OrderStatus.valueOf(statusString);
        return Order.builder()
                .orderId(orderId)
                .userId(1L)
                .orderStatus(status)
                .subtotal(20000L)
                .couponDiscount(0L)
                .finalAmount(20000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Product createTestProduct(Long productId) {
        return Product.builder()
                .productId(productId)
                .productName("Test Product")
                .description("Test Description")
                .price(10000L)
                .totalStock(0)
                .status("IN_STOCK")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private ProductOption createTestProductOption(Long optionId, int stock) {
        return ProductOption.builder()
                .optionId(optionId)
                .productId(1L)
                .name("Test Option")
                .stock(stock)
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
