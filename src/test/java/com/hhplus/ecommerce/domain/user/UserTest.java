package com.hhplus.ecommerce.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * User 도메인 엔티티 단위 테스트
 * - 사용자 생성 및 기본 정보 관리
 * - 충전 잔액 관리
 * - 타임스탐프 추적
 * - 사용자 정보 유효성 검증
 */
@DisplayName("User 도메인 엔티티 테스트")
class UserTest {

    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_EMAIL = "user@example.com";
    private static final String TEST_PASSWORD_HASH = "hashed_password_xyz";
    private static final String TEST_NAME = "김철수";
    private static final String TEST_PHONE = "010-1234-5678";
    private static final Long TEST_BALANCE = 100000L;

    // ========== User 생성 ==========

    @Test
    @DisplayName("User 생성 - 성공")
    void testUserCreation_Success() {
        // When
        User user = User.builder()
                .userId(TEST_USER_ID)
                .email(TEST_EMAIL)
                .passwordHash(TEST_PASSWORD_HASH)
                .name(TEST_NAME)
                .phone(TEST_PHONE)
                .balance(TEST_BALANCE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Then
        assertNotNull(user);
        assertEquals(TEST_USER_ID, user.getUserId());
        assertEquals(TEST_EMAIL, user.getEmail());
        assertEquals(TEST_PASSWORD_HASH, user.getPasswordHash());
        assertEquals(TEST_NAME, user.getName());
        assertEquals(TEST_PHONE, user.getPhone());
        assertEquals(TEST_BALANCE, user.getBalance());
    }

    @Test
    @DisplayName("User 생성 - 초기 잔액 0")
    void testUserCreation_ZeroBalance() {
        // When
        User user = User.builder()
                .userId(TEST_USER_ID)
                .email(TEST_EMAIL)
                .passwordHash(TEST_PASSWORD_HASH)
                .name(TEST_NAME)
                .balance(0L)
                .build();

        // Then
        assertEquals(0L, user.getBalance());
    }

    @Test
    @DisplayName("User 생성 - 다양한 이메일")
    void testUserCreation_DifferentEmails() {
        // When
        User user1 = User.builder()
                .email("john@example.com")
                .build();
        User user2 = User.builder()
                .email("jane.doe@company.co.kr")
                .build();
        User user3 = User.builder()
                .email("user+tag@subdomain.example.com")
                .build();

        // Then
        assertEquals("john@example.com", user1.getEmail());
        assertEquals("jane.doe@company.co.kr", user2.getEmail());
        assertEquals("user+tag@subdomain.example.com", user3.getEmail());
    }

    // ========== User 정보 조회 ==========

    @Test
    @DisplayName("User 조회 - 이메일 확인")
    void testUserRetrieve_Email() {
        // When
        User user = User.builder()
                .email(TEST_EMAIL)
                .build();

        // Then
        assertEquals(TEST_EMAIL, user.getEmail());
    }

    @Test
    @DisplayName("User 조회 - 이름 확인")
    void testUserRetrieve_Name() {
        // When
        User user = User.builder()
                .name(TEST_NAME)
                .build();

        // Then
        assertEquals(TEST_NAME, user.getName());
    }

    @Test
    @DisplayName("User 조회 - 전화번호 확인")
    void testUserRetrieve_Phone() {
        // When
        User user = User.builder()
                .phone(TEST_PHONE)
                .build();

        // Then
        assertEquals(TEST_PHONE, user.getPhone());
    }

    @Test
    @DisplayName("User 조회 - 잔액 확인")
    void testUserRetrieve_Balance() {
        // When
        User user = User.builder()
                .balance(TEST_BALANCE)
                .build();

        // Then
        assertEquals(TEST_BALANCE, user.getBalance());
    }

    // ========== 사용자 정보 변경 ==========

    @Test
    @DisplayName("User 정보 변경 - 이메일 변경")
    void testUserUpdate_Email() {
        // Given
        User user = User.builder()
                .email("old@example.com")
                .build();

        // When
        user.setEmail("new@example.com");

        // Then
        assertEquals("new@example.com", user.getEmail());
    }

    @Test
    @DisplayName("User 정보 변경 - 이름 변경")
    void testUserUpdate_Name() {
        // Given
        User user = User.builder()
                .name("기존이름")
                .build();

        // When
        user.setName("새이름");

        // Then
        assertEquals("새이름", user.getName());
    }

    @Test
    @DisplayName("User 정보 변경 - 전화번호 변경")
    void testUserUpdate_Phone() {
        // Given
        User user = User.builder()
                .phone("010-0000-0000")
                .build();

        // When
        user.setPhone("010-9999-9999");

        // Then
        assertEquals("010-9999-9999", user.getPhone());
    }

    @Test
    @DisplayName("User 정보 변경 - 비밀번호 해시 변경")
    void testUserUpdate_PasswordHash() {
        // Given
        User user = User.builder()
                .passwordHash("old_hash")
                .build();

        // When
        user.setPasswordHash("new_hash");

        // Then
        assertEquals("new_hash", user.getPasswordHash());
    }

    // ========== 충전 잔액 관리 ==========

    @Test
    @DisplayName("잔액 관리 - 충전")
    void testBalanceManagement_Charge() {
        // Given
        User user = User.builder()
                .balance(100000L)
                .build();

        // When
        Long chargeAmount = 50000L;
        user.setBalance(user.getBalance() + chargeAmount);

        // Then
        assertEquals(150000L, user.getBalance());
    }

    @Test
    @DisplayName("잔액 관리 - 사용")
    void testBalanceManagement_Use() {
        // Given
        User user = User.builder()
                .balance(100000L)
                .build();

        // When
        Long usageAmount = 30000L;
        user.setBalance(user.getBalance() - usageAmount);

        // Then
        assertEquals(70000L, user.getBalance());
    }

    @Test
    @DisplayName("잔액 관리 - 복수 거래")
    void testBalanceManagement_MultipleTransactions() {
        // Given
        User user = User.builder()
                .balance(0L)
                .build();

        // When
        user.setBalance(user.getBalance() + 100000L);  // 충전
        user.setBalance(user.getBalance() - 30000L);   // 사용
        user.setBalance(user.getBalance() + 50000L);   // 충전
        user.setBalance(user.getBalance() - 40000L);   // 사용

        // Then
        assertEquals(80000L, user.getBalance());
    }

    @Test
    @DisplayName("잔액 관리 - 0으로 설정")
    void testBalanceManagement_SetToZero() {
        // Given
        User user = User.builder()
                .balance(100000L)
                .build();

        // When
        user.setBalance(0L);

        // Then
        assertEquals(0L, user.getBalance());
    }

    // ========== 타임스탐프 ==========

    @Test
    @DisplayName("타임스탐프 - createdAt 설정")
    void testTimestamp_CreatedAt() {
        // When
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .createdAt(now)
                .build();

        // Then
        assertNotNull(user.getCreatedAt());
        assertEquals(now, user.getCreatedAt());
    }

    @Test
    @DisplayName("타임스탐프 - updatedAt 설정")
    void testTimestamp_UpdatedAt() {
        // When
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .updatedAt(now)
                .build();

        // Then
        assertNotNull(user.getUpdatedAt());
        assertEquals(now, user.getUpdatedAt());
    }

    @Test
    @DisplayName("타임스탐프 - 변경")
    void testTimestamp_Update() {
        // Given
        LocalDateTime originalTime = LocalDateTime.now();
        User user = User.builder()
                .createdAt(originalTime)
                .updatedAt(originalTime)
                .build();

        // When
        LocalDateTime newTime = originalTime.plusHours(1);
        user.setUpdatedAt(newTime);

        // Then
        assertEquals(originalTime, user.getCreatedAt());
        assertEquals(newTime, user.getUpdatedAt());
    }

    // ========== 경계값 테스트 ==========

    @Test
    @DisplayName("경계값 - 최소 잔액 (0)")
    void testBoundary_MinimumBalance() {
        // When
        User user = User.builder()
                .balance(0L)
                .build();

        // Then
        assertEquals(0L, user.getBalance());
    }

    @Test
    @DisplayName("경계값 - 높은 잔액")
    void testBoundary_HighBalance() {
        // When
        User user = User.builder()
                .balance(Long.MAX_VALUE / 2)
                .build();

        // Then
        assertEquals(Long.MAX_VALUE / 2, user.getBalance());
    }

    @Test
    @DisplayName("경계값 - ID 값")
    void testBoundary_IdValue() {
        // When
        User user = User.builder()
                .userId(Long.MAX_VALUE)
                .build();

        // Then
        assertEquals(Long.MAX_VALUE, user.getUserId());
    }

    @Test
    @DisplayName("경계값 - 긴 이메일")
    void testBoundary_LongEmail() {
        // When
        String longEmail = "very.long.email.address.with.many.parts.and.numbers.123456789@example.co.kr";
        User user = User.builder()
                .email(longEmail)
                .build();

        // Then
        assertEquals(longEmail, user.getEmail());
    }

    @Test
    @DisplayName("경계값 - 긴 이름")
    void testBoundary_LongName() {
        // When
        String longName = "매우긴이름이여기에포함되어있습니다아주매우긴이름입니다";
        User user = User.builder()
                .name(longName)
                .build();

        // Then
        assertEquals(longName, user.getName());
    }

    // ========== null 안전성 ==========

    @Test
    @DisplayName("null 안전성 - 모든 필드 null")
    void testNullSafety_AllFields() {
        // When
        User user = User.builder().build();

        // Then
        assertNull(user.getUserId());
        assertNull(user.getEmail());
        assertNull(user.getPasswordHash());
        assertNull(user.getName());
        assertNull(user.getPhone());
        assertNull(user.getBalance());
    }

    @Test
    @DisplayName("null 안전성 - 선택적 필드 설정")
    void testNullSafety_PartialFields() {
        // When
        User user = User.builder()
                .userId(TEST_USER_ID)
                .email(TEST_EMAIL)
                .build();

        // Then
        assertEquals(TEST_USER_ID, user.getUserId());
        assertEquals(TEST_EMAIL, user.getEmail());
        assertNull(user.getPasswordHash());
        assertNull(user.getName());
        assertNull(user.getPhone());
    }

    // ========== NoArgsConstructor/AllArgsConstructor ==========

    @Test
    @DisplayName("NoArgsConstructor 테스트")
    void testNoArgsConstructor() {
        // When
        User user = new User();

        // Then
        assertNull(user.getUserId());
        assertNull(user.getEmail());
    }

    @Test
    @DisplayName("AllArgsConstructor 테스트")
    void testAllArgsConstructor() {
        // When
        LocalDateTime now = LocalDateTime.now();
        User user = new User(
                TEST_USER_ID,
                TEST_EMAIL,
                TEST_PASSWORD_HASH,
                TEST_NAME,
                TEST_PHONE,
                TEST_BALANCE,
                now,
                now
        );

        // Then
        assertEquals(TEST_USER_ID, user.getUserId());
        assertEquals(TEST_EMAIL, user.getEmail());
        assertEquals(TEST_PASSWORD_HASH, user.getPasswordHash());
        assertEquals(TEST_NAME, user.getName());
        assertEquals(TEST_PHONE, user.getPhone());
        assertEquals(TEST_BALANCE, user.getBalance());
    }

    // ========== User 실제 사용 시나리오 ==========

    @Test
    @DisplayName("사용 시나리오 - 사용자 가입")
    void testScenario_UserSignUp() {
        // When
        User user = User.builder()
                .userId(1L)
                .email("newuser@example.com")
                .passwordHash("hashed_password")
                .name("새사용자")
                .phone("010-5555-5555")
                .balance(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Then
        assertEquals("newuser@example.com", user.getEmail());
        assertEquals("새사용자", user.getName());
        assertEquals(0L, user.getBalance());
        assertNotNull(user.getCreatedAt());
    }

    @Test
    @DisplayName("사용 시나리오 - 사용자 정보 수정")
    void testScenario_UpdateUserProfile() {
        // Given
        User user = User.builder()
                .userId(1L)
                .email("user@example.com")
                .name("기존이름")
                .phone("010-1111-1111")
                .build();

        // When
        user.setName("새이름");
        user.setPhone("010-2222-2222");
        user.setUpdatedAt(LocalDateTime.now());

        // Then
        assertEquals("새이름", user.getName());
        assertEquals("010-2222-2222", user.getPhone());
        assertNotNull(user.getUpdatedAt());
    }

    @Test
    @DisplayName("사용 시나리오 - 사용자 충전 및 구매")
    void testScenario_ChargeAndPurchase() {
        // Given
        User user = User.builder()
                .userId(1L)
                .email("buyer@example.com")
                .name("구매자")
                .balance(0L)
                .build();

        // When: 100,000원 충전
        user.setBalance(user.getBalance() + 100000L);

        // When: 35,000원 상품 구매
        user.setBalance(user.getBalance() - 35000L);

        // Then
        assertEquals(65000L, user.getBalance());
    }

    @Test
    @DisplayName("사용 시나리오 - 비밀번호 변경")
    void testScenario_ChangePassword() {
        // Given
        User user = User.builder()
                .userId(1L)
                .email("user@example.com")
                .passwordHash("old_hash_value")
                .build();

        // When
        user.setPasswordHash("new_hash_value");
        user.setUpdatedAt(LocalDateTime.now());

        // Then
        assertEquals("new_hash_value", user.getPasswordHash());
    }
}
