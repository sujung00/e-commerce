package com.hhplus.ecommerce.domain.cart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CartItemNotFoundException 단위 테스트")
class CartItemNotFoundExceptionTest {

    @Test
    @DisplayName("CartItemNotFoundException 생성 및 메시지 검증 - ID 포함")
    void testCartItemNotFoundExceptionWithId() {
        Long cartItemId = 999L;
        CartItemNotFoundException exception = new CartItemNotFoundException(cartItemId);

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).contains("999");
        assertThat(exception.getMessage()).contains("장바구니 아이템을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("CartItemNotFoundException 기본 생성자")
    void testCartItemNotFoundExceptionDefault() {
        CartItemNotFoundException exception = new CartItemNotFoundException();

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).contains("장바구니 아이템을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("CartItemNotFoundException 다양한 ID 값")
    void testCartItemNotFoundExceptionDifferentIds() {
        Long id1 = 1L;
        Long id2 = 100L;
        Long id3 = Long.MAX_VALUE;

        CartItemNotFoundException exception1 = new CartItemNotFoundException(id1);
        CartItemNotFoundException exception2 = new CartItemNotFoundException(id2);
        CartItemNotFoundException exception3 = new CartItemNotFoundException(id3);

        assertThat(exception1.getMessage()).contains("1");
        assertThat(exception2.getMessage()).contains("100");
        assertThat(exception3.getMessage()).contains(String.valueOf(Long.MAX_VALUE));
    }
}
