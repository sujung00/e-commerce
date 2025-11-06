package com.hhplus.ecommerce.infrastructure.persistence.user;

import com.hhplus.ecommerce.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InMemoryUserRepository 단위 테스트
 * - User CRUD 작업 검증
 * - 기본 조회 기능 (findById, existsById)
 * - 초기 데이터 검증
 */
@DisplayName("InMemoryUserRepository 테스트")
class InMemoryUserRepositoryTest {

    private InMemoryUserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
    }

    // ========== User 조회 ==========

    @Test
    @DisplayName("findById - 기존 사용자 조회")
    void testFindById_ExistingUser() {
        // When
        Optional<User> user = userRepository.findById(100L);

        // Then
        assertTrue(user.isPresent());
        assertEquals(100L, user.get().getUserId());
        assertEquals("사용자100", user.get().getName());
        assertEquals("user100@example.com", user.get().getEmail());
    }

    @Test
    @DisplayName("findById - 없는 사용자는 Optional.empty()")
    void testFindById_NonExistent() {
        // When
        Optional<User> user = userRepository.findById(999L);

        // Then
        assertTrue(user.isEmpty());
    }

    @Test
    @DisplayName("findById - 두 번째 샘플 사용자 조회")
    void testFindById_SecondUser() {
        // When
        Optional<User> user = userRepository.findById(101L);

        // Then
        assertTrue(user.isPresent());
        assertEquals(101L, user.get().getUserId());
        assertEquals("사용자101", user.get().getName());
        assertEquals("user101@example.com", user.get().getEmail());
    }

    // ========== User 존재 여부 확인 ==========

    @Test
    @DisplayName("existsById - 기존 사용자 확인")
    void testExistsById_Existing() {
        // When
        boolean exists = userRepository.existsById(100L);

        // Then
        assertTrue(exists);
    }

    @Test
    @DisplayName("existsById - 없는 사용자 확인")
    void testExistsById_NonExistent() {
        // When
        boolean exists = userRepository.existsById(999L);

        // Then
        assertFalse(exists);
    }

    @Test
    @DisplayName("existsById - 여러 사용자 확인")
    void testExistsById_Multiple() {
        // When/Then
        assertTrue(userRepository.existsById(100L));
        assertTrue(userRepository.existsById(101L));
        assertFalse(userRepository.existsById(102L));
    }

    // ========== User 정보 검증 ==========

    @Test
    @DisplayName("사용자 정보 - 이메일 확인")
    void testUserInfo_Email() {
        // When
        Optional<User> user = userRepository.findById(100L);

        // Then
        assertTrue(user.isPresent());
        assertEquals("user100@example.com", user.get().getEmail());
    }

    @Test
    @DisplayName("사용자 정보 - 전화번호 확인")
    void testUserInfo_Phone() {
        // When
        Optional<User> user = userRepository.findById(100L);

        // Then
        assertTrue(user.isPresent());
        assertEquals("010-0000-0000", user.get().getPhone());
    }

    @Test
    @DisplayName("사용자 정보 - 잔액 확인")
    void testUserInfo_Balance() {
        // When
        Optional<User> user = userRepository.findById(100L);

        // Then
        assertTrue(user.isPresent());
        assertEquals(1000000L, user.get().getBalance());
    }

    @Test
    @DisplayName("사용자 정보 - 두 번째 사용자 잔액")
    void testUserInfo_SecondUserBalance() {
        // When
        Optional<User> user = userRepository.findById(101L);

        // Then
        assertTrue(user.isPresent());
        assertEquals(500000L, user.get().getBalance());
    }

    // ========== User 타임스탬프 ==========

    @Test
    @DisplayName("타임스탬프 - createdAt 확인")
    void testTimestamp_CreatedAt() {
        // When
        Optional<User> user = userRepository.findById(100L);

        // Then
        assertTrue(user.isPresent());
        assertNotNull(user.get().getCreatedAt());
        assertTrue(user.get().getCreatedAt().isBefore(LocalDateTime.now().plusMinutes(1)));
    }

    @Test
    @DisplayName("타임스탬프 - updatedAt 확인")
    void testTimestamp_UpdatedAt() {
        // When
        Optional<User> user = userRepository.findById(100L);

        // Then
        assertTrue(user.isPresent());
        assertNotNull(user.get().getUpdatedAt());
        // updatedAt should be at or after createdAt (initial creation sets both)
        assertTrue(user.get().getUpdatedAt().compareTo(user.get().getCreatedAt()) >= 0);
    }

    // ========== 초기화 데이터 검증 ==========

    @Test
    @DisplayName("초기화 데이터 - 기본 사용자 데이터 존재")
    void testInitialData_BasicUsers() {
        // Then
        assertTrue(userRepository.existsById(100L));
        assertTrue(userRepository.existsById(101L));
        assertFalse(userRepository.existsById(102L));
    }

    @Test
    @DisplayName("초기화 데이터 - 첫 번째 사용자 상세정보")
    void testInitialData_FirstUserDetails() {
        // When
        Optional<User> user = userRepository.findById(100L);

        // Then
        assertTrue(user.isPresent());
        User u = user.get();
        assertEquals("사용자100", u.getName());
        assertEquals("user100@example.com", u.getEmail());
        assertEquals("010-0000-0000", u.getPhone());
        assertEquals(1000000L, u.getBalance());
    }

    @Test
    @DisplayName("초기화 데이터 - 두 번째 사용자 상세정보")
    void testInitialData_SecondUserDetails() {
        // When
        Optional<User> user = userRepository.findById(101L);

        // Then
        assertTrue(user.isPresent());
        User u = user.get();
        assertEquals("사용자101", u.getName());
        assertEquals("user101@example.com", u.getEmail());
        assertEquals("010-1111-1111", u.getPhone());
        assertEquals(500000L, u.getBalance());
    }

    // ========== 실제 사용 시나리오 ==========

    @Test
    @DisplayName("사용 시나리오 - 사용자 조회 후 정보 확인")
    void testScenario_FindUserAndCheckInfo() {
        // When
        Optional<User> user = userRepository.findById(100L);

        // Then
        assertTrue(user.isPresent());
        User u = user.get();
        assertNotNull(u.getUserId());
        assertNotNull(u.getName());
        assertNotNull(u.getEmail());
        assertTrue(u.getBalance() >= 0);
    }

    @Test
    @DisplayName("사용 시나리오 - 사용자 존재 확인 후 조회")
    void testScenario_CheckExistenceThenFetch() {
        // When
        long userId = 100L;
        boolean exists = userRepository.existsById(userId);

        // Then
        assertTrue(exists);
        Optional<User> user = userRepository.findById(userId);
        assertTrue(user.isPresent());
    }

    @Test
    @DisplayName("사용 시나리오 - 없는 사용자 처리")
    void testScenario_HandleNonExistentUser() {
        // When
        long userId = 999L;
        boolean exists = userRepository.existsById(userId);
        Optional<User> user = userRepository.findById(userId);

        // Then
        assertFalse(exists);
        assertTrue(user.isEmpty());
    }

    @Test
    @DisplayName("사용 시나리오 - 다양한 사용자 잔액 조회")
    void testScenario_CheckMultipleUserBalances() {
        // When
        Optional<User> user1 = userRepository.findById(100L);
        Optional<User> user2 = userRepository.findById(101L);

        // Then
        assertTrue(user1.isPresent());
        assertTrue(user2.isPresent());
        assertEquals(1000000L, user1.get().getBalance());
        assertEquals(500000L, user2.get().getBalance());
        assertTrue(user1.get().getBalance() > user2.get().getBalance());
    }

    @Test
    @DisplayName("사용 시나리오 - 사용자 정보 무결성 확인")
    void testScenario_UserDataIntegrity() {
        // When
        Optional<User> user = userRepository.findById(100L);

        // Then
        assertTrue(user.isPresent());
        User u = user.get();
        assertNotNull(u.getUserId());
        assertNotNull(u.getName());
        assertNotNull(u.getEmail());
        assertNotNull(u.getPhone());
        assertNotNull(u.getBalance());
        assertNotNull(u.getCreatedAt());
        assertNotNull(u.getUpdatedAt());
        assertFalse(u.getName().isEmpty());
        assertFalse(u.getEmail().isEmpty());
    }
}
