package com.hhplus.ecommerce.domain.user;

/**
 * 사용자를 찾을 수 없을 때 발생하는 예외
 */
public class UserNotFoundException extends RuntimeException {
    private static final String MESSAGE = "존재하지 않는 사용자입니다";

    public UserNotFoundException(Long userId) {
        super(String.format("%s (ID: %d)", MESSAGE, userId));
    }

    public UserNotFoundException() {
        super(MESSAGE);
    }
}
