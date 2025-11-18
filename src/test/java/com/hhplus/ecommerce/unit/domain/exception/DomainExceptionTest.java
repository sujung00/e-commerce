package com.hhplus.ecommerce.unit.domain.exception;

import com.hhplus.ecommerce.domain.cart.CartItemNotFoundException;
import com.hhplus.ecommerce.domain.cart.InvalidQuantityException;
import com.hhplus.ecommerce.domain.coupon.CouponNotFoundException;
import com.hhplus.ecommerce.domain.order.OrderNotFoundException;
import com.hhplus.ecommerce.domain.product.ProductNotFoundException;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 도메인 예외 단위 테스트
 * - 모든 Domain 레이어 예외 클래스의 생성 및 메시지 검증
 * - 예외 메시지 형식 확인
 * - 예외 던지기 및 처리 가능성 확인
 * - 경계값 테스트 (최대값, 최소값)
 */
@DisplayName("Domain 예외 단위 테스트")
class DomainExceptionTest {

    // ========== InvalidQuantityException ==========

    @Test
    @DisplayName("InvalidQuantityException - 기본 생성")
    void testInvalidQuantityException_Default() {
        // When
        InvalidQuantityException exception = new InvalidQuantityException();

        // Then
        assertNotNull(exception);
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("수량은 1 이상 1000 이하여야 합니다"));
    }

    @Test
    @DisplayName("InvalidQuantityException - 수량값으로 생성")
    void testInvalidQuantityException_WithQuantity() {
        // When
        InvalidQuantityException exception = new InvalidQuantityException(2000);

        // Then
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("수량은 1 이상 1000 이하여야 합니다"));
        assertTrue(exception.getMessage().contains("2000"));
    }

    @Test
    @DisplayName("InvalidQuantityException - 경계값 0 (너무 작음)")
    void testInvalidQuantityException_BoundaryZero() {
        // When
        InvalidQuantityException exception = new InvalidQuantityException(0);

        // Then
        assertTrue(exception.getMessage().contains("0"));
    }

    @Test
    @DisplayName("InvalidQuantityException - 경계값 1001 (너무 큼)")
    void testInvalidQuantityException_BoundaryOver() {
        // When
        InvalidQuantityException exception = new InvalidQuantityException(1001);

        // Then
        assertTrue(exception.getMessage().contains("1001"));
    }

    @Test
    @DisplayName("InvalidQuantityException - 음수")
    void testInvalidQuantityException_Negative() {
        // When
        InvalidQuantityException exception = new InvalidQuantityException(-5);

        // Then
        assertTrue(exception.getMessage().contains("-5"));
    }

    @Test
    @DisplayName("InvalidQuantityException - 예외 던지기")
    void testInvalidQuantityException_ThrowAndCatch() {
        // When/Then
        assertThrows(InvalidQuantityException.class, () -> {
            throw new InvalidQuantityException(2000);
        });
    }

    // ========== CartItemNotFoundException ==========

    @Test
    @DisplayName("CartItemNotFoundException - 기본 생성")
    void testCartItemNotFoundException_Default() {
        // When
        CartItemNotFoundException exception = new CartItemNotFoundException();

        // Then
        assertNotNull(exception);
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("장바구니 아이템을 찾을 수 없습니다"));
    }

    @Test
    @DisplayName("CartItemNotFoundException - ID값으로 생성")
    void testCartItemNotFoundException_WithId() {
        // When
        CartItemNotFoundException exception = new CartItemNotFoundException(123L);

        // Then
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("장바구니 아이템을 찾을 수 없습니다"));
        assertTrue(exception.getMessage().contains("123"));
    }

    @Test
    @DisplayName("CartItemNotFoundException - 큰 ID값")
    void testCartItemNotFoundException_LargeId() {
        // When
        CartItemNotFoundException exception = new CartItemNotFoundException(Long.MAX_VALUE);

        // Then
        assertTrue(exception.getMessage().contains("9223372036854775807"));
    }

    @Test
    @DisplayName("CartItemNotFoundException - 예외 던지기")
    void testCartItemNotFoundException_ThrowAndCatch() {
        // When/Then
        assertThrows(CartItemNotFoundException.class, () -> {
            throw new CartItemNotFoundException(1L);
        });
    }

    // ========== ProductNotFoundException ==========

    @Test
    @DisplayName("ProductNotFoundException - ID값으로 생성")
    void testProductNotFoundException_WithId() {
        // When
        ProductNotFoundException exception = new ProductNotFoundException(100L);

        // Then
        assertNotNull(exception);
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("상품을 찾을 수 없습니다"));
        assertEquals("PRODUCT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    @DisplayName("ProductNotFoundException - 커스텀 메시지로 생성")
    void testProductNotFoundException_WithMessage() {
        // When
        String customMessage = "요청하신 상품을 찾을 수 없습니다";
        ProductNotFoundException exception = new ProductNotFoundException(customMessage);

        // Then
        assertTrue(exception.getMessage().contains(customMessage));
        assertEquals("PRODUCT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    @DisplayName("ProductNotFoundException - 에러 코드 확인")
    void testProductNotFoundException_ErrorCode() {
        // When
        ProductNotFoundException exception = new ProductNotFoundException(1L);

        // Then
        assertEquals("PRODUCT_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    @DisplayName("ProductNotFoundException - 큰 ID값")
    void testProductNotFoundException_LargeId() {
        // When
        ProductNotFoundException exception = new ProductNotFoundException(Long.MAX_VALUE);

        // Then
        assertTrue(exception.getMessage().contains("상품을 찾을 수 없습니다"));
    }

    @Test
    @DisplayName("ProductNotFoundException - 예외 던지기")
    void testProductNotFoundException_ThrowAndCatch() {
        // When/Then
        assertThrows(ProductNotFoundException.class, () -> {
            throw new ProductNotFoundException(1L);
        });
    }

    // ========== UserNotFoundException ==========

    @Test
    @DisplayName("UserNotFoundException - 기본 생성")
    void testUserNotFoundException_Default() {
        // When
        UserNotFoundException exception = new UserNotFoundException();

        // Then
        assertNotNull(exception);
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("존재하지 않는 사용자입니다"));
    }

    @Test
    @DisplayName("UserNotFoundException - ID값으로 생성")
    void testUserNotFoundException_WithId() {
        // When
        UserNotFoundException exception = new UserNotFoundException(50L);

        // Then
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("존재하지 않는 사용자입니다"));
        assertTrue(exception.getMessage().contains("50"));
    }

    @Test
    @DisplayName("UserNotFoundException - 큰 ID값")
    void testUserNotFoundException_LargeId() {
        // When
        UserNotFoundException exception = new UserNotFoundException(Long.MAX_VALUE);

        // Then
        assertTrue(exception.getMessage().contains("존재하지 않는 사용자입니다"));
        assertTrue(exception.getMessage().contains("9223372036854775807"));
    }

    @Test
    @DisplayName("UserNotFoundException - 예외 던지기")
    void testUserNotFoundException_ThrowAndCatch() {
        // When/Then
        assertThrows(UserNotFoundException.class, () -> {
            throw new UserNotFoundException(1L);
        });
    }

    // ========== CouponNotFoundException ==========

    @Test
    @DisplayName("CouponNotFoundException - ID값으로 생성")
    void testCouponNotFoundException_WithId() {
        // When
        CouponNotFoundException exception = new CouponNotFoundException(10L);

        // Then
        assertNotNull(exception);
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("쿠폰을 찾을 수 없습니다"));
        assertEquals(10L, exception.getCouponId());
    }

    @Test
    @DisplayName("CouponNotFoundException - 에러 코드 확인")
    void testCouponNotFoundException_ErrorCode() {
        // When
        CouponNotFoundException exception = new CouponNotFoundException(1L);

        // Then
        assertEquals("COUPON_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    @DisplayName("CouponNotFoundException - getCouponId 메서드")
    void testCouponNotFoundException_GetCouponId() {
        // When
        long couponId = 999L;
        CouponNotFoundException exception = new CouponNotFoundException(couponId);

        // Then
        assertEquals(couponId, exception.getCouponId());
    }

    @Test
    @DisplayName("CouponNotFoundException - 큰 ID값")
    void testCouponNotFoundException_LargeId() {
        // When
        CouponNotFoundException exception = new CouponNotFoundException(Long.MAX_VALUE);

        // Then
        assertEquals(Long.MAX_VALUE, exception.getCouponId());
    }

    @Test
    @DisplayName("CouponNotFoundException - 예외 던지기")
    void testCouponNotFoundException_ThrowAndCatch() {
        // When/Then
        assertThrows(CouponNotFoundException.class, () -> {
            throw new CouponNotFoundException(1L);
        });
    }

    // ========== OrderNotFoundException ==========

    @Test
    @DisplayName("OrderNotFoundException - ID값으로 생성")
    void testOrderNotFoundException_WithId() {
        // When
        OrderNotFoundException exception = new OrderNotFoundException(100L);

        // Then
        assertNotNull(exception);
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("주문을 찾을 수 없습니다"));
        assertTrue(exception.getMessage().contains("100"));
    }

    @Test
    @DisplayName("OrderNotFoundException - 커스텀 메시지로 생성")
    void testOrderNotFoundException_WithMessage() {
        // When
        String customMessage = "주문이 취소되었습니다";
        OrderNotFoundException exception = new OrderNotFoundException(customMessage);

        // Then
        assertTrue(exception.getMessage().contains(customMessage));
    }

    @Test
    @DisplayName("OrderNotFoundException - 큰 ID값")
    void testOrderNotFoundException_LargeId() {
        // When
        OrderNotFoundException exception = new OrderNotFoundException(Long.MAX_VALUE);

        // Then
        assertTrue(exception.getMessage().contains("주문을 찾을 수 없습니다"));
        assertTrue(exception.getMessage().contains("9223372036854775807"));
    }

    @Test
    @DisplayName("OrderNotFoundException - 예외 던지기")
    void testOrderNotFoundException_ThrowAndCatch() {
        // When/Then
        assertThrows(OrderNotFoundException.class, () -> {
            throw new OrderNotFoundException(1L);
        });
    }

    // ========== 예외 상속 관계 ==========

    @Test
    @DisplayName("예외 상속 - InvalidQuantityException은 RuntimeException")
    void testExceptionInheritance_InvalidQuantity() {
        // When
        InvalidQuantityException exception = new InvalidQuantityException(100);

        // Then
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    @DisplayName("예외 상속 - CartItemNotFoundException은 RuntimeException")
    void testExceptionInheritance_CartItemNotFound() {
        // When
        CartItemNotFoundException exception = new CartItemNotFoundException(1L);

        // Then
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    @DisplayName("예외 상속 - ProductNotFoundException은 RuntimeException")
    void testExceptionInheritance_ProductNotFound() {
        // When
        ProductNotFoundException exception = new ProductNotFoundException(1L);

        // Then
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    @DisplayName("예외 상속 - UserNotFoundException은 RuntimeException")
    void testExceptionInheritance_UserNotFound() {
        // When
        UserNotFoundException exception = new UserNotFoundException(1L);

        // Then
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    @DisplayName("예외 상속 - CouponNotFoundException은 RuntimeException")
    void testExceptionInheritance_CouponNotFound() {
        // When
        CouponNotFoundException exception = new CouponNotFoundException(1L);

        // Then
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    @DisplayName("예외 상속 - OrderNotFoundException은 RuntimeException")
    void testExceptionInheritance_OrderNotFound() {
        // When
        OrderNotFoundException exception = new OrderNotFoundException(1L);

        // Then
        assertTrue(exception instanceof RuntimeException);
    }

    // ========== 예외 캐칭 및 처리 ==========

    @Test
    @DisplayName("예외 처리 - 동일한 RuntimeException으로 캐치")
    void testExceptionHandling_CatchAsRuntimeException() {
        // When/Then
        assertThrows(RuntimeException.class, () -> {
            throw new InvalidQuantityException(0);
        });
    }

    @Test
    @DisplayName("예외 처리 - 특정 예외로 캐치")
    void testExceptionHandling_CatchSpecificException() {
        // When/Then
        assertThrows(InvalidQuantityException.class, () -> {
            throw new InvalidQuantityException(0);
        });
    }

    @Test
    @DisplayName("예외 처리 - 여러 예외 처리")
    void testExceptionHandling_MultipleExceptions() {
        // When/Then
        assertThrows(RuntimeException.class, () -> {
            throw new ProductNotFoundException(1L);
        });

        assertThrows(RuntimeException.class, () -> {
            throw new UserNotFoundException(1L);
        });

        assertThrows(RuntimeException.class, () -> {
            throw new CouponNotFoundException(1L);
        });
    }

    // ========== 메시지 일관성 테스트 ==========

    @Test
    @DisplayName("메시지 일관성 - 같은 ID로 생성된 예외는 같은 메시지")
    void testMessageConsistency_SameId() {
        // When
        ProductNotFoundException exception1 = new ProductNotFoundException(1L);
        ProductNotFoundException exception2 = new ProductNotFoundException(1L);

        // Then
        assertEquals(exception1.getMessage(), exception2.getMessage());
    }

    @Test
    @DisplayName("메시지 일관성 - ProductNotFoundException은 ID와 무관하게 동일한 메시지")
    void testMessageConsistency_ProductNotFound() {
        // When
        ProductNotFoundException exception1 = new ProductNotFoundException(1L);
        ProductNotFoundException exception2 = new ProductNotFoundException(2L);

        // Then
        // ProductNotFoundException은 ID와 무관하게 항상 같은 메시지 반환
        assertEquals(exception1.getMessage(), exception2.getMessage());
    }

    // ========== null 안전성 ==========

    @Test
    @DisplayName("null 안전성 - ProductNotFoundException 메시지 null 체크")
    void testNullSafety_ProductNotFoundMessage() {
        // When
        ProductNotFoundException exception = new ProductNotFoundException(1L);

        // Then
        assertNotNull(exception.getMessage());
        assertFalse(exception.getMessage().isEmpty());
    }

    @Test
    @DisplayName("null 안전성 - UserNotFoundException 메시지 null 체크")
    void testNullSafety_UserNotFoundMessage() {
        // When
        UserNotFoundException exception = new UserNotFoundException(1L);

        // Then
        assertNotNull(exception.getMessage());
        assertFalse(exception.getMessage().isEmpty());
    }

    // ========== 실제 사용 시나리오 ==========

    @Test
    @DisplayName("사용 시나리오 - 수량 검증 실패")
    void testScenario_InvalidQuantityValidation() {
        // When/Then
        assertThrows(InvalidQuantityException.class, () -> {
            int quantity = 2000;
            if (quantity < 1 || quantity > 1000) {
                throw new InvalidQuantityException(quantity);
            }
        });
    }

    @Test
    @DisplayName("사용 시나리오 - 상품 조회 실패")
    void testScenario_ProductLookupFails() {
        // When/Then
        assertThrows(ProductNotFoundException.class, () -> {
            long productId = 999L;
            // 상품이 존재하지 않음
            throw new ProductNotFoundException(productId);
        });
    }

    @Test
    @DisplayName("사용 시나리오 - 사용자 조회 실패")
    void testScenario_UserLookupFails() {
        // When/Then
        assertThrows(UserNotFoundException.class, () -> {
            long userId = 999L;
            // 사용자가 존재하지 않음
            throw new UserNotFoundException(userId);
        });
    }

    @Test
    @DisplayName("사용 시나리오 - 쿠폰 사용 실패")
    void testScenario_CouponUsageFails() {
        // When/Then
        assertThrows(CouponNotFoundException.class, () -> {
            long couponId = 999L;
            // 쿠폰이 존재하지 않음
            throw new CouponNotFoundException(couponId);
        });
    }

    @Test
    @DisplayName("사용 시나리오 - 주문 조회 실패")
    void testScenario_OrderLookupFails() {
        // When/Then
        assertThrows(OrderNotFoundException.class, () -> {
            long orderId = 999L;
            // 주문이 존재하지 않음
            throw new OrderNotFoundException(orderId);
        });
    }

    @Test
    @DisplayName("사용 시나리오 - 장바구니 아이템 조회 실패")
    void testScenario_CartItemLookupFails() {
        // When/Then
        assertThrows(CartItemNotFoundException.class, () -> {
            long cartItemId = 999L;
            // 장바구니 아이템이 존재하지 않음
            throw new CartItemNotFoundException(cartItemId);
        });
    }
}
