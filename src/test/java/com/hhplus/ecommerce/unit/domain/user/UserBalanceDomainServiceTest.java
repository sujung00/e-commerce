package com.hhplus.ecommerce.unit.domain.user;

import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserBalanceDomainService;
import com.hhplus.ecommerce.domain.user.InsufficientBalanceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserBalanceDomainService - 사용자 잔액 도메인 비즈니스 로직")
class UserBalanceDomainServiceTest {

    private UserBalanceDomainService userBalanceDomainService;

    @BeforeEach
    void setUp() {
        userBalanceDomainService = new UserBalanceDomainService();
    }

    // ==================== validateSufficientBalance Tests ====================

    @Test
    @DisplayName("잔액 검증 - 충분한 잔액")
    void validateSufficientBalance_WithSufficientBalance_Success() {
        // Given
        User user = createTestUser(1L, 100000L);

        // When & Then
        assertDoesNotThrow(() -> userBalanceDomainService.validateSufficientBalance(user, 50000L));
    }

    @Test
    @DisplayName("잔액 검증 - 정확히 같은 금액")
    void validateSufficientBalance_WithExactAmount_Success() {
        // Given
        User user = createTestUser(1L, 100000L);

        // When & Then
        assertDoesNotThrow(() -> userBalanceDomainService.validateSufficientBalance(user, 100000L));
    }

    @Test
    @DisplayName("잔액 검증 - 부족한 잔액")
    void validateSufficientBalance_WithInsufficientBalance_ThrowsException() {
        // Given
        User user = createTestUser(1L, 50000L);

        // When & Then
        assertThrows(InsufficientBalanceException.class,
                () -> userBalanceDomainService.validateSufficientBalance(user, 100000L));
    }

    @Test
    @DisplayName("잔액 검증 - 0원 잔액")
    void validateSufficientBalance_WithZeroBalance_ThrowsException() {
        // Given
        User user = createTestUser(1L, 0L);

        // When & Then
        assertThrows(InsufficientBalanceException.class,
                () -> userBalanceDomainService.validateSufficientBalance(user, 1L));
    }

    // ==================== deductBalance Tests ====================

    @Test
    @DisplayName("잔액 차감 - 정상 케이스")
    void deductBalance_WithSufficientBalance_Success() {
        // Given
        User user = createTestUser(1L, 100000L);

        // When
        userBalanceDomainService.deductBalance(user, 30000L);

        // Then
        assertEquals(70000L, user.getBalance());
    }

    @Test
    @DisplayName("잔액 차감 - 0원으로 만들기")
    void deductBalance_DeductAllBalance_Success() {
        // Given
        User user = createTestUser(1L, 50000L);

        // When
        userBalanceDomainService.deductBalance(user, 50000L);

        // Then
        assertEquals(0L, user.getBalance());
    }

    @Test
    @DisplayName("잔액 차감 - 부족한 잔액")
    void deductBalance_WithInsufficientBalance_ThrowsException() {
        // Given
        User user = createTestUser(1L, 30000L);

        // When & Then
        assertThrows(InsufficientBalanceException.class,
                () -> userBalanceDomainService.deductBalance(user, 50000L));
    }

    @Test
    @DisplayName("잔액 차감 - 음수 금액")
    void deductBalance_WithNegativeAmount_ThrowsException() {
        // Given
        User user = createTestUser(1L, 100000L);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> userBalanceDomainService.deductBalance(user, -10000L));
    }

    @Test
    @DisplayName("잔액 차감 - 0원 차감")
    void deductBalance_WithZeroAmount_ThrowsException() {
        // Given
        User user = createTestUser(1L, 100000L);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> userBalanceDomainService.deductBalance(user, 0L));
    }

    // ==================== chargeBalance Tests ====================

    @Test
    @DisplayName("잔액 충전 - 정상 케이스")
    void chargeBalance_WithValidAmount_Success() {
        // Given
        User user = createTestUser(1L, 100000L);

        // When
        userBalanceDomainService.chargeBalance(user, 50000L);

        // Then
        assertEquals(150000L, user.getBalance());
    }

    @Test
    @DisplayName("잔액 충전 - 0원 잔액에 충전")
    void chargeBalance_FromZeroBalance_Success() {
        // Given
        User user = createTestUser(1L, 0L);

        // When
        userBalanceDomainService.chargeBalance(user, 100000L);

        // Then
        assertEquals(100000L, user.getBalance());
    }

    @Test
    @DisplayName("잔액 충전 - 음수 금액")
    void chargeBalance_WithNegativeAmount_ThrowsException() {
        // Given
        User user = createTestUser(1L, 100000L);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> userBalanceDomainService.chargeBalance(user, -10000L));
    }

    @Test
    @DisplayName("잔액 충전 - 0원 충전")
    void chargeBalance_WithZeroAmount_ThrowsException() {
        // Given
        User user = createTestUser(1L, 100000L);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> userBalanceDomainService.chargeBalance(user, 0L));
    }

    // ==================== refundBalance Tests ====================

    @Test
    @DisplayName("잔액 환불 - 정상 케이스")
    void refundBalance_WithValidAmount_Success() {
        // Given
        User user = createTestUser(1L, 100000L);

        // When
        userBalanceDomainService.refundBalance(user, 30000L);

        // Then
        assertEquals(130000L, user.getBalance());
    }

    @Test
    @DisplayName("잔액 환불 - 0원 잔액에 환불")
    void refundBalance_FromZeroBalance_Success() {
        // Given
        User user = createTestUser(1L, 0L);

        // When
        userBalanceDomainService.refundBalance(user, 50000L);

        // Then
        assertEquals(50000L, user.getBalance());
    }

    @Test
    @DisplayName("잔액 환불 - 음수 금액")
    void refundBalance_WithNegativeAmount_ThrowsException() {
        // Given
        User user = createTestUser(1L, 100000L);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> userBalanceDomainService.refundBalance(user, -10000L));
    }

    @Test
    @DisplayName("잔액 환불 - 0원 환불")
    void refundBalance_WithZeroAmount_ThrowsException() {
        // Given
        User user = createTestUser(1L, 100000L);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> userBalanceDomainService.refundBalance(user, 0L));
    }

    // ==================== validateAndDeductBalance Tests ====================

    @Test
    @DisplayName("잔액 검증 및 차감 - 정상 케이스")
    void validateAndDeductBalance_WithSufficientBalance_Success() {
        // Given
        User user = createTestUser(1L, 100000L);

        // When
        userBalanceDomainService.validateAndDeductBalance(user, 30000L);

        // Then
        assertEquals(70000L, user.getBalance());
    }

    @Test
    @DisplayName("잔액 검증 및 차감 - 부족한 잔액")
    void validateAndDeductBalance_WithInsufficientBalance_ThrowsException() {
        // Given
        User user = createTestUser(1L, 30000L);

        // When & Then
        assertThrows(InsufficientBalanceException.class,
                () -> userBalanceDomainService.validateAndDeductBalance(user, 50000L));

        // Balance should not be changed when validation fails
        assertEquals(30000L, user.getBalance());
    }

    @Test
    @DisplayName("잔액 검증 및 차감 - 음수 금액")
    void validateAndDeductBalance_WithNegativeAmount_ThrowsException() {
        // Given
        User user = createTestUser(1L, 100000L);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> userBalanceDomainService.validateAndDeductBalance(user, -10000L));

        // Balance should not be changed when validation fails
        assertEquals(100000L, user.getBalance());
    }

    // ==================== Multiple Operations Tests ====================

    @Test
    @DisplayName("여러 작업 - 충전 후 차감")
    void multipleOperations_ChargeAndDeduct_Success() {
        // Given
        User user = createTestUser(1L, 100000L);

        // When
        userBalanceDomainService.chargeBalance(user, 50000L); // 150000
        userBalanceDomainService.deductBalance(user, 30000L); // 120000
        userBalanceDomainService.refundBalance(user, 20000L); // 140000

        // Then
        assertEquals(140000L, user.getBalance());
    }

    @Test
    @DisplayName("여러 작업 - updatedAt이 변경됨")
    void multipleOperations_UpdatedAtIsUpdated() {
        // Given
        User user = createTestUser(1L, 100000L);
        LocalDateTime initialUpdatedAt = user.getUpdatedAt();

        // Sleep to ensure time has passed
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When
        userBalanceDomainService.chargeBalance(user, 50000L);

        // Then
        assertTrue(user.getUpdatedAt().isAfter(initialUpdatedAt));
    }

    // ==================== Helper Methods ====================

    private User createTestUser(Long userId, Long balance) {
        return User.builder()
                .userId(userId)
                .email("test" + userId + "@example.com")
                .name("TestUser")
                .phone("010-1234-5678")
                .balance(balance)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
