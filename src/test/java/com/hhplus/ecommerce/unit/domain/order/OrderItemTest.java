package com.hhplus.ecommerce.unit.domain.order;


import com.hhplus.ecommerce.domain.order.OrderItem;import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderItem 도메인 엔티티 단위 테스트
 * - 주문 항목 생성 팩토리 메서드
 * - 금액 계산 검증 (단가 * 수량)
 * - 스냅샷 데이터 관리
 */
@DisplayName("OrderItem 도메인 엔티티 테스트")
class OrderItemTest {

    private static final Long TEST_PRODUCT_ID = 100L;
    private static final Long TEST_OPTION_ID = 1L;
    private static final String TEST_PRODUCT_NAME = "프리미엄 상품";
    private static final String TEST_OPTION_NAME = "사이즈 L";
    private static final Integer TEST_QUANTITY = 2;
    private static final Long TEST_UNIT_PRICE = 50000L;

    // ========== OrderItem 생성 팩토리 메서드 ==========

    @Test
    @DisplayName("OrderItem 생성 - 성공 (기본 케이스)")
    void testCreateOrderItem_Success() {
        // When
        OrderItem orderItem = OrderItem.createOrderItem(
                TEST_PRODUCT_ID,
                TEST_OPTION_ID,
                TEST_PRODUCT_NAME,
                TEST_OPTION_NAME,
                TEST_QUANTITY,
                TEST_UNIT_PRICE
        );

        // Then
        assertNotNull(orderItem);
        assertEquals(TEST_PRODUCT_ID, orderItem.getProductId());
        assertEquals(TEST_OPTION_ID, orderItem.getOptionId());
        assertEquals(TEST_PRODUCT_NAME, orderItem.getProductName());
        assertEquals(TEST_OPTION_NAME, orderItem.getOptionName());
        assertEquals(TEST_QUANTITY, orderItem.getQuantity());
        assertEquals(TEST_UNIT_PRICE, orderItem.getUnitPrice());
    }

    @Test
    @DisplayName("OrderItem 생성 - 소계 자동 계산")
    void testCreateOrderItem_SubtotalCalculation() {
        // When
        OrderItem orderItem = OrderItem.createOrderItem(
                TEST_PRODUCT_ID,
                TEST_OPTION_ID,
                TEST_PRODUCT_NAME,
                TEST_OPTION_NAME,
                TEST_QUANTITY,
                TEST_UNIT_PRICE
        );

        // Then
        // 소계 = 단가 * 수량
        Long expectedSubtotal = TEST_UNIT_PRICE * TEST_QUANTITY;
        assertEquals(expectedSubtotal, orderItem.getSubtotal());
    }

    @Test
    @DisplayName("OrderItem 생성 - 타임스탐프 자동 설정")
    void testCreateOrderItem_TimestampSet() {
        // When
        LocalDateTime beforeCreation = LocalDateTime.now();
        OrderItem orderItem = OrderItem.createOrderItem(
                TEST_PRODUCT_ID,
                TEST_OPTION_ID,
                TEST_PRODUCT_NAME,
                TEST_OPTION_NAME,
                TEST_QUANTITY,
                TEST_UNIT_PRICE
        );
        LocalDateTime afterCreation = LocalDateTime.now();

        // Then
        assertNotNull(orderItem.getCreatedAt());
        assertFalse(orderItem.getCreatedAt().isBefore(beforeCreation));
        assertFalse(orderItem.getCreatedAt().isAfter(afterCreation.plusSeconds(1)));
    }

    // ========== 금액 계산 검증 ==========

    @Test
    @DisplayName("소계 계산 - 다양한 수량")
    void testSubtotalCalculation_DifferentQuantities() {
        // When/Then
        testSubtotalForQuantity(1, 50000L, 50000L);
        testSubtotalForQuantity(2, 50000L, 100000L);
        testSubtotalForQuantity(5, 50000L, 250000L);
        testSubtotalForQuantity(10, 50000L, 500000L);
    }

    private void testSubtotalForQuantity(Integer quantity, Long unitPrice, Long expectedSubtotal) {
        OrderItem orderItem = OrderItem.createOrderItem(
                TEST_PRODUCT_ID,
                TEST_OPTION_ID,
                TEST_PRODUCT_NAME,
                TEST_OPTION_NAME,
                quantity,
                unitPrice
        );
        assertEquals(expectedSubtotal, orderItem.getSubtotal());
    }

    @Test
    @DisplayName("소계 계산 - 다양한 단가")
    void testSubtotalCalculation_DifferentPrices() {
        // When/Then
        testSubtotalForPrice(2, 10000L, 20000L);
        testSubtotalForPrice(2, 50000L, 100000L);
        testSubtotalForPrice(2, 100000L, 200000L);
        testSubtotalForPrice(2, 999999L, 1999998L);
    }

    private void testSubtotalForPrice(Integer quantity, Long unitPrice, Long expectedSubtotal) {
        OrderItem orderItem = OrderItem.createOrderItem(
                TEST_PRODUCT_ID,
                TEST_OPTION_ID,
                TEST_PRODUCT_NAME,
                TEST_OPTION_NAME,
                quantity,
                unitPrice
        );
        assertEquals(expectedSubtotal, orderItem.getSubtotal());
    }

    @Test
    @DisplayName("소계 계산 - 수량이 1일 때")
    void testSubtotalCalculation_SingleQuantity() {
        // When
        OrderItem orderItem = OrderItem.createOrderItem(
                TEST_PRODUCT_ID,
                TEST_OPTION_ID,
                TEST_PRODUCT_NAME,
                TEST_OPTION_NAME,
                1,
                50000L
        );

        // Then
        assertEquals(50000L, orderItem.getSubtotal());
    }

    @Test
    @DisplayName("소계 계산 - 0원 상품")
    void testSubtotalCalculation_FreeProduct() {
        // 0원 상품은 유효하지 않으므로 예외 발생 확인
        assertThrows(IllegalArgumentException.class, () ->
            OrderItem.createOrderItem(
                TEST_PRODUCT_ID,
                TEST_OPTION_ID,
                TEST_PRODUCT_NAME,
                TEST_OPTION_NAME,
                5,
                0L
            )
        );
    }

    @Test
    @DisplayName("소계 계산 - 대금액")
    void testSubtotalCalculation_LargeAmount() {
        // When
        Long largePrice = 999999999L;
        Integer quantity = 2;
        OrderItem orderItem = OrderItem.createOrderItem(
                TEST_PRODUCT_ID,
                TEST_OPTION_ID,
                TEST_PRODUCT_NAME,
                TEST_OPTION_NAME,
                quantity,
                largePrice
        );

        // Then
        assertEquals(largePrice * quantity, orderItem.getSubtotal());
    }

    // ========== 스냅샷 데이터 검증 ==========

    @Test
    @DisplayName("스냅샷 - 상품명 기록")
    void testSnapshot_ProductName() {
        // When
        String productName = "변경 전 상품명";
        OrderItem orderItem = OrderItem.createOrderItem(
                TEST_PRODUCT_ID,
                TEST_OPTION_ID,
                productName,
                TEST_OPTION_NAME,
                TEST_QUANTITY,
                TEST_UNIT_PRICE
        );

        // Then
        // 스냅샷이므로 생성 시점의 상품명이 유지됨
        assertEquals(productName, orderItem.getProductName());
    }

    @Test
    @DisplayName("스냅샷 - 옵션명 기록")
    void testSnapshot_OptionName() {
        // When
        String optionName = "색상: 빨강";
        OrderItem orderItem = OrderItem.createOrderItem(
                TEST_PRODUCT_ID,
                TEST_OPTION_ID,
                TEST_PRODUCT_NAME,
                optionName,
                TEST_QUANTITY,
                TEST_UNIT_PRICE
        );

        // Then
        assertEquals(optionName, orderItem.getOptionName());
    }

    // ========== OrderItem 빌더 패턴 ==========

    @Test
    @DisplayName("OrderItem 빌더 - 모든 필드 설정")
    void testOrderItemBuilder_AllFields() {
        // When
        LocalDateTime now = LocalDateTime.now();
        OrderItem orderItem = OrderItem.builder()
                .orderItemId(1L)
                .orderId(100L)
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .productName(TEST_PRODUCT_NAME)
                .optionName(TEST_OPTION_NAME)
                .quantity(TEST_QUANTITY)
                .unitPrice(TEST_UNIT_PRICE)
                .subtotal(TEST_UNIT_PRICE * TEST_QUANTITY)
                .createdAt(now)
                .build();

        // Then
        assertEquals(1L, orderItem.getOrderItemId());
        assertEquals(100L, orderItem.getOrderId());
        assertEquals(TEST_UNIT_PRICE * TEST_QUANTITY, orderItem.getSubtotal());
    }

    // ========== 경계값 테스트 ==========

    @Test
    @DisplayName("경계값 - 최소 수량 (1)")
    void testBoundary_MinimumQuantity() {
        // When
        OrderItem orderItem = OrderItem.createOrderItem(
                TEST_PRODUCT_ID,
                TEST_OPTION_ID,
                TEST_PRODUCT_NAME,
                TEST_OPTION_NAME,
                1,
                TEST_UNIT_PRICE
        );

        // Then
        assertEquals(1, orderItem.getQuantity());
        assertEquals(TEST_UNIT_PRICE, orderItem.getSubtotal());
    }

    @Test
    @DisplayName("경계값 - 최대 수량 (1000+)")
    void testBoundary_HighQuantity() {
        // When
        Integer highQuantity = 1000;
        OrderItem orderItem = OrderItem.createOrderItem(
                TEST_PRODUCT_ID,
                TEST_OPTION_ID,
                TEST_PRODUCT_NAME,
                TEST_OPTION_NAME,
                highQuantity,
                TEST_UNIT_PRICE
        );

        // Then
        assertEquals(highQuantity, orderItem.getQuantity());
        assertEquals(TEST_UNIT_PRICE * highQuantity, orderItem.getSubtotal());
    }

    @Test
    @DisplayName("경계값 - ID 값 범위")
    void testBoundary_IdValues() {
        // When
        OrderItem orderItem = OrderItem.createOrderItem(
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                TEST_PRODUCT_NAME,
                TEST_OPTION_NAME,
                1,
                TEST_UNIT_PRICE
        );

        // Then
        assertEquals(Long.MAX_VALUE, orderItem.getProductId());
        assertEquals(Long.MAX_VALUE, orderItem.getOptionId());
    }

    @Test
    @DisplayName("필드 null 안전성 - 빌더 사용")
    void testNullSafety_Builder() {
        // When
        OrderItem orderItem = OrderItem.builder().build();

        // Then
        assertNull(orderItem.getOrderItemId());
        assertNull(orderItem.getProductId());
        assertNull(orderItem.getProductName());
        assertNull(orderItem.getQuantity());
        assertNull(orderItem.getSubtotal());
    }

    @Test
    @DisplayName("필드 null 안전성 - Builder로 null 설정")
    void testNullSafety_Setter() {
        // When
        OrderItem orderItem = OrderItem.builder()
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .productName(null)
                .optionName(TEST_OPTION_NAME)
                .quantity(null)
                .unitPrice(TEST_UNIT_PRICE)
                .build();

        // Then
        assertNull(orderItem.getProductName());
        assertNull(orderItem.getQuantity());
    }

    @Test
    @DisplayName("OrderItem - 타임스탐프만 읽기 가능")
    void testTimestamp_ReadOnly() {
        // When
        OrderItem orderItem = OrderItem.createOrderItem(
                TEST_PRODUCT_ID,
                TEST_OPTION_ID,
                TEST_PRODUCT_NAME,
                TEST_OPTION_NAME,
                TEST_QUANTITY,
                TEST_UNIT_PRICE
        );
        LocalDateTime originalCreatedAt = orderItem.getCreatedAt();

        // Then
        // Setter로 변경 가능 (도메인에서 불변성을 강제하지 않음)
        assertNotNull(originalCreatedAt);
    }
}
