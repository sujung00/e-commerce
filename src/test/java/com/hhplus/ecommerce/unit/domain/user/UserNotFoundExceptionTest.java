package com.hhplus.ecommerce.unit.domain.user;


import com.hhplus.ecommerce.domain.user.UserNotFoundException;import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UserNotFoundException 단위 테스트")
class UserNotFoundExceptionTest {

    @Test
    @DisplayName("UserNotFoundException 생성 및 메시지 검증")
    void testUserNotFoundExceptionWithId() {
        Long userId = 999L;
        UserNotFoundException exception = new UserNotFoundException(userId);

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).contains("999");
    }

    @Test
    @DisplayName("UserNotFoundException 기본 생성자")
    void testUserNotFoundExceptionDefault() {
        UserNotFoundException exception = new UserNotFoundException();

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("UserNotFoundException 다양한 ID 값")
    void testUserNotFoundExceptionDifferentIds() {
        Long id1 = 1L;
        Long id2 = 100L;
        Long id3 = Long.MAX_VALUE;

        UserNotFoundException exception1 = new UserNotFoundException(id1);
        UserNotFoundException exception2 = new UserNotFoundException(id2);
        UserNotFoundException exception3 = new UserNotFoundException(id3);

        assertThat(exception1.getMessage()).contains("1");
        assertThat(exception2.getMessage()).contains("100");
        assertThat(exception3.getMessage()).contains(String.valueOf(Long.MAX_VALUE));
    }
}
