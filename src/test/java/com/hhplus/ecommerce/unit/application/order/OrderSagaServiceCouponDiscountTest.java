package com.hhplus.ecommerce.unit.application.order;

import com.hhplus.ecommerce.application.order.*;
import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import com.hhplus.ecommerce.application.order.dto.OrderItemCommand;
import com.hhplus.ecommerce.application.order.saga.orchestration.OrderSagaOrchestrator;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("[Unit] OrderSagaService - calculateCouponDiscount")
class OrderSagaServiceCouponDiscountTest {

    @Mock private OrderSagaOrchestrator orderSagaOrchestrator;
    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private OrderCalculator orderCalculator;
    @Mock private OutboxEventPublisher outboxEventPublisher;
    @Mock private ObjectMapper objectMapper;
    @Mock private CouponRepository couponRepository;

    @InjectMocks
    private OrderSagaService orderSagaService;

    private static final Long USER_ID   = 1L;
    private static final Long COUPON_ID = 10L;
    private static final Long SUBTOTAL  = 100_000L;

    private List<OrderItemDto> singleItem() {
        return List.of(OrderItemDto.builder()
                .productId(1L).optionId(1L).quantity(1)
                .build());
    }

    private Order stubOrder() {
        return Order.builder()
                .orderId(99L)
                .userId(USER_ID)
                .build();
    }

    @Nested
    @DisplayName("FIXED_AMOUNT 쿠폰")
    class FixedAmount {

        @Test
        @DisplayName("정액 5000원 할인이 Orchestrator에 전달된다")
        void fixedAmountDiscount() {
            Coupon coupon = Coupon.builder()
                    .couponId(COUPON_ID)
                    .discountType("FIXED_AMOUNT")
                    .discountAmount(5_000L)
                    .discountRate(BigDecimal.ZERO)
                    .validFrom(LocalDateTime.now().minusDays(1))
                    .validUntil(LocalDateTime.now().plusDays(30))
                    .build();

            given(couponRepository.findById(COUPON_ID)).willReturn(Optional.of(coupon));
            given(orderCalculator.calculateSubtotal(anyList())).willReturn(SUBTOTAL);
            given(orderSagaOrchestrator.executeSaga(
                    eq(USER_ID), anyList(), eq(COUPON_ID), eq(5_000L), eq(SUBTOTAL), anyLong()))
                    .willReturn(stubOrder());

            orderSagaService.createOrderWithPayment(USER_ID, singleItem(), COUPON_ID, SUBTOTAL - 5_000L);

            then(orderSagaOrchestrator).should().executeSaga(
                    eq(USER_ID), anyList(), eq(COUPON_ID), eq(5_000L), eq(SUBTOTAL), anyLong());
        }
    }

    @Nested
    @DisplayName("PERCENTAGE 쿠폰")
    class Percentage {

        @Test
        @DisplayName("10% 할인 = subtotal * 0.1 이 Orchestrator에 전달된다")
        void percentageDiscount() {
            Coupon coupon = Coupon.builder()
                    .couponId(COUPON_ID)
                    .discountType("PERCENTAGE")
                    .discountAmount(0L)
                    .discountRate(new BigDecimal("0.10"))
                    .validFrom(LocalDateTime.now().minusDays(1))
                    .validUntil(LocalDateTime.now().plusDays(30))
                    .build();

            given(couponRepository.findById(COUPON_ID)).willReturn(Optional.of(coupon));
            given(orderCalculator.calculateSubtotal(anyList())).willReturn(SUBTOTAL);
            given(orderSagaOrchestrator.executeSaga(
                    eq(USER_ID), anyList(), eq(COUPON_ID), eq(10_000L), eq(SUBTOTAL), anyLong()))
                    .willReturn(stubOrder());

            orderSagaService.createOrderWithPayment(USER_ID, singleItem(), COUPON_ID, SUBTOTAL - 10_000L);

            then(orderSagaOrchestrator).should().executeSaga(
                    eq(USER_ID), anyList(), eq(COUPON_ID), eq(10_000L), eq(SUBTOTAL), anyLong());
        }
    }

    @Nested
    @DisplayName("쿠폰 없음 (couponId = null)")
    class NoCoupon {

        @Test
        @DisplayName("쿠폰 ID가 null이면 할인액 0으로 Orchestrator 호출")
        void noCouponDiscount() {
            given(orderCalculator.calculateSubtotal(anyList())).willReturn(SUBTOTAL);
            given(orderSagaOrchestrator.executeSaga(
                    eq(USER_ID), anyList(), isNull(), eq(0L), eq(SUBTOTAL), anyLong()))
                    .willReturn(stubOrder());

            orderSagaService.createOrderWithPayment(USER_ID, singleItem(), null, SUBTOTAL);

            then(couponRepository).shouldHaveNoInteractions();
            then(orderSagaOrchestrator).should().executeSaga(
                    eq(USER_ID), anyList(), isNull(), eq(0L), eq(SUBTOTAL), anyLong());
        }
    }

    @Nested
    @DisplayName("쿠폰 미존재 예외")
    class CouponNotFound {

        @Test
        @DisplayName("존재하지 않는 쿠폰 ID → 쿠폰 ID를 포함한 예외 발생")
        void couponNotFound() {
            given(couponRepository.findById(COUPON_ID)).willReturn(Optional.empty());
            given(orderCalculator.calculateSubtotal(anyList())).willReturn(SUBTOTAL);

            // createOrderWithPayment 는 내부 예외를 RuntimeException 으로 래핑한다
            assertThatThrownBy(() ->
                    orderSagaService.createOrderWithPayment(USER_ID, singleItem(), COUPON_ID, SUBTOTAL))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(String.valueOf(COUPON_ID));
        }
    }
}