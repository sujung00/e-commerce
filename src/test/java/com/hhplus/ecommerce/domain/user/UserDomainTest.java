package com.hhplus.ecommerce.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * User 도메인 엔티티 순수 단위 테스트
 *
 * 테스트 대상:
 * - 사용자 생성 팩토리 메서드
 * - 잔액 충전 로직
 * - 잔액 출금 로직
 * - 잔액 환불 로직
 * - 잔액 검증 메서드들
 *
 * 특징: Mock 없이 실제 도메인 객체만 사용
 */
@DisplayName("User 도메인 엔티티 순수 단위 테스트")
class UserDomainTest {

    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_EMAIL = "user@example.com";
    private static final String TEST_PASSWORD_HASH = "hashed_password";
    private static final String TEST_NAME = "테스트유저";
    private static final String TEST_PHONE = "010-1234-5678";

    // ========== User 생성 팩토리 메서드 ==========

    @Nested
    @DisplayName("User 생성")
    class UserCreationTests {

        @Test
        @DisplayName("User 생성 - 성공 (모든 필드)")
        void testCreateUser_Success() {
            // When
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);

            // Then
            assertNotNull(user);
            assertEquals(TEST_EMAIL, user.getEmail());
            assertEquals(TEST_PASSWORD_HASH, user.getPasswordHash());
            assertEquals(TEST_NAME, user.getName());
            assertEquals(TEST_PHONE, user.getPhone());
            assertEquals(0L, user.getBalance());  // 초기 잔액은 0
            assertNotNull(user.getCreatedAt());
            assertNotNull(user.getUpdatedAt());
        }

        @Test
        @DisplayName("User 생성 - 초기 잔액은 0")
        void testCreateUser_InitialBalanceZero() {
            // When
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);

            // Then
            assertEquals(0L, user.getBalance());
        }

        @Test
        @DisplayName("User 생성 - 빈 이메일 거절")
        void testCreateUser_EmptyEmail() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () ->
                User.createUser("", TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE)
            );
            assertThrows(IllegalArgumentException.class, () ->
                User.createUser("   ", TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE)
            );
        }

        @Test
        @DisplayName("User 생성 - null 이메일 거절")
        void testCreateUser_NullEmail() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () ->
                User.createUser(null, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE)
            );
        }
    }

    // ========== 잔액 충전 로직 ==========

    @Nested
    @DisplayName("잔액 충전")
    class BalanceChargeTests {

        @Test
        @DisplayName("잔액 충전 - 성공")
        void testChargeBalance_Success() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);
            assertEquals(0L, user.getBalance());

            // When
            user.chargeBalance(50000L);

            // Then
            assertEquals(50000L, user.getBalance());
        }

        @Test
        @DisplayName("잔액 충전 - 누적")
        void testChargeBalance_Accumulation() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);

            // When
            user.chargeBalance(30000L);
            user.chargeBalance(20000L);
            user.chargeBalance(50000L);

            // Then
            assertEquals(100000L, user.getBalance());
        }

        @Test
        @DisplayName("잔액 충전 - 0원 거절")
        void testChargeBalance_ZeroAmount() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> user.chargeBalance(0L));
        }

        @Test
        @DisplayName("잔액 충전 - 음수 거절")
        void testChargeBalance_NegativeAmount() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> user.chargeBalance(-10000L));
        }

        @Test
        @DisplayName("잔액 충전 - 대금액")
        void testChargeBalance_LargeAmount() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);
            Long largeAmount = 999999999L;

            // When
            user.chargeBalance(largeAmount);

            // Then
            assertEquals(largeAmount, user.getBalance());
        }

        @Test
        @DisplayName("잔액 충전 - updatedAt 갱신")
        void testChargeBalance_UpdatesTimestamp() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);
            var beforeCharge = user.getUpdatedAt();

            // When
            user.chargeBalance(10000L);

            // Then
            assertTrue(user.getUpdatedAt().isAfter(beforeCharge));
        }
    }

    // ========== 잔액 출금(결제) 로직 ==========

    @Nested
    @DisplayName("잔액 출금")
    class BalanceDeductTests {

        @Test
        @DisplayName("잔액 출금 - 성공")
        void testDeductBalance_Success() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);
            user.chargeBalance(100000L);

            // When
            user.deductBalance(30000L);

            // Then
            assertEquals(70000L, user.getBalance());
        }

        @Test
        @DisplayName("잔액 출금 - 정확히 맞는 금액")
        void testDeductBalance_ExactAmount() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);
            user.chargeBalance(50000L);

            // When
            user.deductBalance(50000L);

            // Then
            assertEquals(0L, user.getBalance());
        }

        @Test
        @DisplayName("잔액 출금 - 부족한 경우 예외 발생")
        void testDeductBalance_InsufficientBalance() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);
            user.chargeBalance(30000L);

            // When & Then
            assertThrows(InsufficientBalanceException.class, () ->
                user.deductBalance(50000L)
            );
        }

        @Test
        @DisplayName("잔액 출금 - 0원 거절")
        void testDeductBalance_ZeroAmount() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);
            user.chargeBalance(100000L);

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> user.deductBalance(0L));
        }

        @Test
        @DisplayName("잔액 출금 - 음수 거절")
        void testDeductBalance_NegativeAmount() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);
            user.chargeBalance(100000L);

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> user.deductBalance(-10000L));
        }

        @Test
        @DisplayName("잔액 출금 - 잔액이 0일 때 출금 불가")
        void testDeductBalance_ZeroBalance() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);

            // When & Then
            assertThrows(InsufficientBalanceException.class, () ->
                user.deductBalance(1L)
            );
        }

        @Test
        @DisplayName("잔액 출금 - updatedAt 갱신")
        void testDeductBalance_UpdatesTimestamp() throws InterruptedException {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);
            user.chargeBalance(100000L);
            Thread.sleep(1);  // 마이크로초 단위 타이밍 차이 발생
            var beforeDeduct = user.getUpdatedAt();

            // When
            user.deductBalance(10000L);

            // Then
            assertTrue(user.getUpdatedAt().isAfter(beforeDeduct) || user.getUpdatedAt().isEqual(beforeDeduct.plusNanos(1)));
        }
    }

    // ========== 잔액 환불 로직 ==========

    @Nested
    @DisplayName("잔액 환불")
    class BalanceRefundTests {

        @Test
        @DisplayName("잔액 환불 - 성공")
        void testRefundBalance_Success() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);
            user.chargeBalance(100000L);
            user.deductBalance(50000L);  // 잔액: 50000
            assertEquals(50000L, user.getBalance());

            // When
            user.refundBalance(50000L);

            // Then
            assertEquals(100000L, user.getBalance());
        }

        @Test
        @DisplayName("잔액 환불 - 0원 거절")
        void testRefundBalance_ZeroAmount() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> user.refundBalance(0L));
        }

        @Test
        @DisplayName("잔액 환불 - 음수 거절")
        void testRefundBalance_NegativeAmount() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> user.refundBalance(-10000L));
        }

        @Test
        @DisplayName("잔액 환불 - updatedAt 갱신")
        void testRefundBalance_UpdatesTimestamp() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);
            user.chargeBalance(100000L);
            var beforeRefund = user.getUpdatedAt();

            // When
            user.refundBalance(10000L);

            // Then
            assertTrue(user.getUpdatedAt().isAfter(beforeRefund));
        }
    }

    // ========== 잔액 검증 메서드 ==========

    @Nested
    @DisplayName("잔액 검증 메서드")
    class BalanceValidationTests {

        @Test
        @DisplayName("canDeduct - 충분한 잔액")
        void testCanDeduct_Sufficient() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);
            user.chargeBalance(100000L);

            // When & Then
            assertTrue(user.canDeduct(50000L));
            assertTrue(user.canDeduct(100000L));
            assertFalse(user.canDeduct(150000L));
        }

        @Test
        @DisplayName("hasSufficientBalance - 충분한 잔액")
        void testHasSufficientBalance() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);
            user.chargeBalance(100000L);

            // When & Then
            assertTrue(user.hasSufficientBalance(100000L));
            assertTrue(user.hasSufficientBalance(50000L));
            assertFalse(user.hasSufficientBalance(150000L));
        }

        @Test
        @DisplayName("getCurrentBalance - 현재 잔액 조회")
        void testGetCurrentBalance() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);
            user.chargeBalance(100000L);

            // When
            Long currentBalance = user.getCurrentBalance();

            // Then
            assertEquals(100000L, currentBalance);
        }

        @Test
        @DisplayName("hasNoBalance - 잔액 0 확인")
        void testHasNoBalance() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, TEST_PHONE);

            // When & Then
            assertTrue(user.hasNoBalance());

            // When
            user.chargeBalance(100000L);

            // Then
            assertFalse(user.hasNoBalance());
        }
    }

    // ========== 사용자 정보 갱신 ==========

    @Nested
    @DisplayName("사용자 정보 갱신")
    class UserInfoUpdateTests {

        @Test
        @DisplayName("사용자 정보 갱신 - 이름 변경")
        void testUpdateUserInfo_Name() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, "원래이름", TEST_PHONE);
            var beforeUpdate = user.getUpdatedAt();

            // When
            user.updateUserInfo("새로운이름", null);

            // Then
            assertEquals("새로운이름", user.getName());
            assertTrue(user.getUpdatedAt().isAfter(beforeUpdate));
        }

        @Test
        @DisplayName("사용자 정보 갱신 - 전화번호 변경")
        void testUpdateUserInfo_Phone() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, TEST_NAME, "010-1111-1111");

            // When
            user.updateUserInfo(null, "010-9999-9999");

            // Then
            assertEquals("010-9999-9999", user.getPhone());
        }

        @Test
        @DisplayName("사용자 정보 갱신 - 둘다 변경")
        void testUpdateUserInfo_Both() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, "원래이름", "010-1111-1111");

            // When
            user.updateUserInfo("새로운이름", "010-9999-9999");

            // Then
            assertEquals("새로운이름", user.getName());
            assertEquals("010-9999-9999", user.getPhone());
        }

        @Test
        @DisplayName("사용자 정보 갱신 - 공백은 무시")
        void testUpdateUserInfo_BlankIgnored() {
            // Given
            User user = User.createUser(TEST_EMAIL, TEST_PASSWORD_HASH, "원래이름", "010-1111-1111");

            // When
            user.updateUserInfo("   ", "   ");

            // Then
            assertEquals("원래이름", user.getName());
            assertEquals("010-1111-1111", user.getPhone());
        }
    }

    // ========== 실제 비즈니스 시나리오 ==========

    @Nested
    @DisplayName("실제 비즈니스 시나리오")
    class RealWorldScenarios {

        @Test
        @DisplayName("시나리오 1: 사용자 가입, 충전, 구매, 환불")
        void scenario1_SignupChargeAndRefund() {
            // Given: 사용자 가입
            User user = User.createUser("customer@example.com", "hashed_pwd", "고객1", "010-1234-5678");
            assertEquals(0L, user.getBalance());

            // When: 잔액 충전 (50,000원)
            user.chargeBalance(50000L);
            assertEquals(50000L, user.getBalance());

            // When: 상품 구매 (30,000원)
            user.deductBalance(30000L);
            assertEquals(20000L, user.getBalance());

            // When: 구매 취소로 환불 (30,000원)
            user.refundBalance(30000L);
            assertEquals(50000L, user.getBalance());
        }

        @Test
        @DisplayName("시나리오 2: 여러 건 구매 후 잔액 확인")
        void scenario2_MultipleTransactions() {
            // Given
            User user = User.createUser("customer@example.com", "hashed_pwd", "고객1", "010-1234-5678");
            user.chargeBalance(100000L);

            // When: 첫 구매
            user.deductBalance(25000L);  // 남은 금액: 75,000
            assertTrue(user.hasSufficientBalance(50000L));

            // When: 두 번째 구매
            user.deductBalance(40000L);  // 남은 금액: 35,000
            assertFalse(user.hasSufficientBalance(50000L));

            // When: 추가 충전
            user.chargeBalance(20000L);  // 남은 금액: 55,000

            // When: 세 번째 구매
            user.deductBalance(55000L);  // 남은 금액: 0

            // Then
            assertEquals(0L, user.getBalance());
            assertTrue(user.hasNoBalance());
        }

        @Test
        @DisplayName("시나리오 3: InsufficientBalanceException 정보 확인")
        void scenario3_InsufficientBalanceExceptionDetails() {
            // Given
            User user = User.createUser("customer@example.com", "hashed_pwd", "고객1", "010-1234-5678");
            user.chargeBalance(30000L);

            // When & Then
            InsufficientBalanceException exception = assertThrows(
                InsufficientBalanceException.class,
                () -> user.deductBalance(50000L)
            );

            // Then: 예외 정보 확인
            assertNull(exception.getUserId());  // 새로 생성한 User는 userId가 null
            assertEquals(30000L, exception.getCurrentBalance());
            assertEquals(50000L, exception.getRequiredAmount());
            assertEquals(20000L, exception.getShortfall());  // 50,000 - 30,000
        }
    }
}
