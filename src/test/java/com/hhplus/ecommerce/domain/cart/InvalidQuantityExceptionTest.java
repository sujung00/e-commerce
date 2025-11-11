package com.hhplus.ecommerce.domain.cart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("InvalidQuantityException 단위 테스트")
class InvalidQuantityExceptionTest {

    @Test
    @DisplayName("InvalidQuantityException 생성 및 메시지 검증 - 수량 포함")
    void testInvalidQuantityExceptionWithQuantity() {
        Integer quantity = 0;
        InvalidQuantityException exception = new InvalidQuantityException(quantity);

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).contains("0");
        assertThat(exception.getMessage()).contains("수량은 1 이상 1000 이하여야 합니다");
    }

    @Test
    @DisplayName("InvalidQuantityException 기본 생성자")
    void testInvalidQuantityExceptionDefault() {
        InvalidQuantityException exception = new InvalidQuantityException();

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).contains("수량은 1 이상 1000 이하여야 합니다");
    }

    @Test
    @DisplayName("InvalidQuantityException 다양한 수량 값")
    void testInvalidQuantityExceptionDifferentQuantities() {
        Integer quantity1 = 0;
        Integer quantity2 = -5;
        Integer quantity3 = 1001;

        InvalidQuantityException exception1 = new InvalidQuantityException(quantity1);
        InvalidQuantityException exception2 = new InvalidQuantityException(quantity2);
        InvalidQuantityException exception3 = new InvalidQuantityException(quantity3);

        assertThat(exception1.getMessage()).contains("0");
        assertThat(exception2.getMessage()).contains("-5");
        assertThat(exception3.getMessage()).contains("1001");
    }
}
