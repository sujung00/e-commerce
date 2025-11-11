package com.hhplus.ecommerce.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("User 단위 테스트")
class UserTest {

    @Test
    @DisplayName("사용자 생성 - 성공")
    void testUserCreation() {
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .userId(1L)
                .email("user@example.com")
                .passwordHash("hashed_password")
                .name("John Doe")
                .phone("010-1234-5678")
                .balance(100000L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(user.getUserId()).isEqualTo(1L);
        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getName()).isEqualTo("John Doe");
        assertThat(user.getBalance()).isEqualTo(100000L);
    }

    @Test
    @DisplayName("사용자 잔액 조회")
    void testUserBalance() {
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .userId(1L)
                .email("user@example.com")
                .passwordHash("hashed_password")
                .name("John Doe")
                .balance(50000L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(user.getBalance()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("사용자 정보 필드 검증")
    void testUserFieldValidation() {
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .userId(5L)
                .email("test@example.com")
                .passwordHash("pass1234")
                .name("Test User")
                .phone("010-9999-8888")
                .balance(200000L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(user.getPasswordHash()).isEqualTo("pass1234");
        assertThat(user.getCreatedAt()).isEqualTo(now);
        assertThat(user.getPhone()).isEqualTo("010-9999-8888");
    }

    @Test
    @DisplayName("사용자 잔액 업데이트")
    void testUserBalanceUpdate() {
        User user = User.builder()
                .userId(1L)
                .balance(100000L)
                .build();

        user.setBalance(150000L);

        assertThat(user.getBalance()).isEqualTo(150000L);
    }

    @Test
    @DisplayName("사용자 정보 업데이트")
    void testUserInfoUpdate() {
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .userId(1L)
                .email("old@example.com")
                .name("Old Name")
                .updatedAt(now)
                .build();

        user.setEmail("new@example.com");
        user.setName("New Name");
        LocalDateTime updatedTime = now.plusHours(1);
        user.setUpdatedAt(updatedTime);

        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getName()).isEqualTo("New Name");
        assertThat(user.getUpdatedAt()).isEqualTo(updatedTime);
    }

    @Test
    @DisplayName("사용자 타임스탐프")
    void testUserTimestamp() {
        LocalDateTime created = LocalDateTime.now();
        LocalDateTime updated = created.plusHours(2);

        User user = User.builder()
                .userId(1L)
                .createdAt(created)
                .updatedAt(updated)
                .build();

        assertThat(user.getCreatedAt()).isEqualTo(created);
        assertThat(user.getUpdatedAt()).isEqualTo(updated);
    }

    @Test
    @DisplayName("사용자 null 필드")
    void testUserNullFields() {
        User user = User.builder().build();

        assertThat(user.getUserId()).isNull();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getName()).isNull();
        assertThat(user.getBalance()).isNull();
    }

    @Test
    @DisplayName("사용자 경계값 - 0 잔액")
    void testUserZeroBalance() {
        User user = User.builder()
                .userId(1L)
                .balance(0L)
                .build();

        assertThat(user.getBalance()).isEqualTo(0L);
    }

    @Test
    @DisplayName("사용자 경계값 - 큰 잔액")
    void testUserLargeBalance() {
        User user = User.builder()
                .userId(1L)
                .balance(Long.MAX_VALUE)
                .build();

        assertThat(user.getBalance()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("사용자 경계값 - 큰 ID")
    void testUserLargeId() {
        User user = User.builder()
                .userId(Long.MAX_VALUE)
                .build();

        assertThat(user.getUserId()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("사용자 잔액 감소 시나리오")
    void testUserBalanceDecreaseScenario() {
        User user = User.builder()
                .userId(1L)
                .balance(100000L)
                .build();

        Long deductedAmount = 30000L;
        user.setBalance(user.getBalance() - deductedAmount);

        assertThat(user.getBalance()).isEqualTo(70000L);
    }

    @Test
    @DisplayName("사용자 잔액 증가 시나리오")
    void testUserBalanceIncreaseScenario() {
        User user = User.builder()
                .userId(1L)
                .balance(100000L)
                .build();

        Long chargedAmount = 50000L;
        user.setBalance(user.getBalance() + chargedAmount);

        assertThat(user.getBalance()).isEqualTo(150000L);
    }
}
