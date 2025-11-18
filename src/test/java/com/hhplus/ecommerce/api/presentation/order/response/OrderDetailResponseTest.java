package com.hhplus.ecommerce.api.presentation.order.response;

import com.hhplus.ecommerce.presentation.order.response.OrderDetailResponse;
import com.hhplus.ecommerce.presentation.order.response.OrderItemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OrderDetailResponse 단위 테스트")
class OrderDetailResponseTest {

    @Test
    @DisplayName("OrderDetailResponse 빌더를 통한 생성")
    void testOrderDetailResponseBuilder() {
        LocalDateTime now = LocalDateTime.now();
        OrderDetailResponse response = OrderDetailResponse.builder()
                .orderId(1L)
                .userId(100L)
                .orderStatus("COMPLETED")
                .subtotal(100000L)
                .couponDiscount(10000L)
                .couponId(1L)
                .finalAmount(90000L)
                .orderItems(new ArrayList<>())
                .createdAt(now)
                .build();

        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo(100L);
        assertThat(response.getOrderStatus()).isEqualTo("COMPLETED");
        assertThat(response.getSubtotal()).isEqualTo(100000L);
        assertThat(response.getCouponDiscount()).isEqualTo(10000L);
        assertThat(response.getCouponId()).isEqualTo(1L);
        assertThat(response.getFinalAmount()).isEqualTo(90000L);
        assertThat(response.getCreatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("OrderDetailResponse 기본 생성자")
    void testOrderDetailResponseNoArgsConstructor() {
        OrderDetailResponse response = new OrderDetailResponse();

        assertThat(response.getOrderId()).isNull();
        assertThat(response.getUserId()).isNull();
        assertThat(response.getOrderStatus()).isNull();
        assertThat(response.getSubtotal()).isNull();
    }

    @Test
    @DisplayName("OrderDetailResponse 전체 생성자")
    void testOrderDetailResponseAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        List<OrderItemResponse> items = new ArrayList<>();

        OrderDetailResponse response = new OrderDetailResponse(
                1L, 100L, "COMPLETED", 100000L, 10000L, 1L, 90000L, items, now
        );

        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo(100L);
        assertThat(response.getOrderStatus()).isEqualTo("COMPLETED");
        assertThat(response.getFinalAmount()).isEqualTo(90000L);
    }

    @Test
    @DisplayName("OrderDetailResponse getter 테스트")
    void testOrderDetailResponseGetter() {
        LocalDateTime now = LocalDateTime.now();
        OrderDetailResponse response = OrderDetailResponse.builder()
                .orderId(1L)
                .userId(100L)
                .orderStatus("PENDING")
                .subtotal(50000L)
                .couponDiscount(5000L)
                .couponId(2L)
                .finalAmount(45000L)
                .orderItems(new ArrayList<>())
                .createdAt(now)
                .build();

        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo(100L);
        assertThat(response.getOrderStatus()).isEqualTo("PENDING");
        assertThat(response.getFinalAmount()).isEqualTo(45000L);
    }

    @Test
    @DisplayName("OrderDetailResponse 다양한 주문 상태")
    void testOrderDetailResponseDifferentStatus() {
        String[] statuses = {"PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED"};

        for (String status : statuses) {
            OrderDetailResponse response = OrderDetailResponse.builder()
                    .orderStatus(status)
                    .build();
            assertThat(response.getOrderStatus()).isEqualTo(status);
        }
    }

    @Test
    @DisplayName("OrderDetailResponse 쿠폰 할인 없음")
    void testOrderDetailResponseNoCoupon() {
        OrderDetailResponse response = OrderDetailResponse.builder()
                .orderId(1L)
                .userId(100L)
                .subtotal(100000L)
                .couponDiscount(0L)
                .couponId(null)
                .finalAmount(100000L)
                .build();

        assertThat(response.getCouponDiscount()).isEqualTo(0L);
        assertThat(response.getCouponId()).isNull();
        assertThat(response.getFinalAmount()).isEqualTo(response.getSubtotal());
    }

    @Test
    @DisplayName("OrderDetailResponse 주문 아이템 포함")
    void testOrderDetailResponseWithItems() {
        List<OrderItemResponse> items = new ArrayList<>();
        // 실제 items는 OrderItemResponse로 구성되지만 여기서는 빈 리스트 사용

        OrderDetailResponse response = OrderDetailResponse.builder()
                .orderId(1L)
                .orderItems(items)
                .build();

        assertThat(response.getOrderItems()).isNotNull();
    }

    @Test
    @DisplayName("OrderDetailResponse 경계값 - 0원 주문")
    void testOrderDetailResponseZeroAmount() {
        OrderDetailResponse response = OrderDetailResponse.builder()
                .orderId(1L)
                .subtotal(0L)
                .couponDiscount(0L)
                .finalAmount(0L)
                .build();

        assertThat(response.getSubtotal()).isEqualTo(0L);
        assertThat(response.getFinalAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("OrderDetailResponse 경계값 - 큰 금액")
    void testOrderDetailResponseLargeAmount() {
        OrderDetailResponse response = OrderDetailResponse.builder()
                .orderId(Long.MAX_VALUE)
                .userId(Long.MAX_VALUE)
                .subtotal(Long.MAX_VALUE / 2)
                .finalAmount(Long.MAX_VALUE / 2)
                .build();

        assertThat(response.getOrderId()).isEqualTo(Long.MAX_VALUE);
        assertThat(response.getSubtotal()).isEqualTo(Long.MAX_VALUE / 2);
    }

    @Test
    @DisplayName("OrderDetailResponse null 필드")
    void testOrderDetailResponseNullFields() {
        OrderDetailResponse response = OrderDetailResponse.builder().build();

        assertThat(response.getOrderId()).isNull();
        assertThat(response.getUserId()).isNull();
        assertThat(response.getOrderStatus()).isNull();
        assertThat(response.getSubtotal()).isNull();
        assertThat(response.getCouponDiscount()).isNull();
        assertThat(response.getCouponId()).isNull();
        assertThat(response.getFinalAmount()).isNull();
        assertThat(response.getOrderItems()).isNull();
        assertThat(response.getCreatedAt()).isNull();
    }

    @Test
    @DisplayName("OrderDetailResponse 할인율 검증")
    void testOrderDetailResponseDiscountValidation() {
        Long subtotal = 100000L;
        Long discount = 20000L;
        Long finalAmount = subtotal - discount;

        OrderDetailResponse response = OrderDetailResponse.builder()
                .subtotal(subtotal)
                .couponDiscount(discount)
                .finalAmount(finalAmount)
                .build();

        assertThat(response.getFinalAmount())
                .isEqualTo(response.getSubtotal() - response.getCouponDiscount());
    }

    @Test
    @DisplayName("OrderDetailResponse 타임스탐프")
    void testOrderDetailResponseTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime pastTime = now.minusHours(1);

        OrderDetailResponse response = OrderDetailResponse.builder()
                .orderId(1L)
                .createdAt(pastTime)
                .build();

        assertThat(response.getCreatedAt()).isEqualTo(pastTime);
        assertThat(response.getCreatedAt()).isBefore(LocalDateTime.now());
    }
}
