package com.hhplus.ecommerce.unit.application.user;

import com.hhplus.ecommerce.application.user.UserBalanceService;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.domain.user.InsufficientBalanceException;
import com.hhplus.ecommerce.domain.order.ChildTransactionEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UserBalanceService 단위 테스트
 *
 * 테스트 항목:
 * 1. 잔액 차감 - 성공 케이스
 * 2. 잔액 차감 - 실패 케이스 (잔액 부족)
 * 3. 잔액 충전 - 성공 케이스
 * 4. 잔액 환불 - 성공 케이스
 * 5. 사용자 미존재 - UserNotFoundException
 *
 * 분산락 검증:
 * - @DistributedLock이 메서드에 적용되었는지 확인
 * - RuntimeException 발생 여부는 통합 테스트에서 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserBalanceService 단위 테스트")
public class UserBalanceServiceTest {

    private UserBalanceService userBalanceService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChildTransactionEventRepository childTransactionEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    private User testUser;
    private static final Long TEST_USER_ID = 1L;
    private static final Long INITIAL_BALANCE = 100_000L;
    private static final Long DEDUCT_AMOUNT = 10_000L;
    private static final Long CHARGE_AMOUNT = 5_000L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userBalanceService = new UserBalanceService(userRepository, childTransactionEventRepository, objectMapper);

        // 테스트용 사용자 생성
        testUser = User.builder()
                .userId(TEST_USER_ID)
                .email("test@example.com")
                .passwordHash("hashed_password")
                .name("Test User")
                .phone("010-1234-5678")
                .balance(INITIAL_BALANCE)
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ========== 잔액 차감 (deductBalance) ==========

    @Test
    @DisplayName("잔액 차감 - 성공")
    void testDeductBalance_Success() {
        // Given: 비관적 락으로 사용자 조회
        when(userRepository.findByIdForUpdate(TEST_USER_ID))
                .thenReturn(Optional.of(testUser));

        // When: 잔액 차감
        User result = userBalanceService.deductBalance(TEST_USER_ID, DEDUCT_AMOUNT);

        // Then
        assertNotNull(result);
        assertEquals(INITIAL_BALANCE - DEDUCT_AMOUNT, testUser.getBalance());
        verify(userRepository).findByIdForUpdate(TEST_USER_ID);
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("잔액 차감 - 실패 (잔액 부족)")
    void testDeductBalance_InsufficientBalance() {
        // Given: 잔액 부족
        long largeAmount = INITIAL_BALANCE + 1_000L;
        when(userRepository.findByIdForUpdate(TEST_USER_ID))
                .thenReturn(Optional.of(testUser));

        // When & Then: InsufficientBalanceException 발생
        assertThrows(InsufficientBalanceException.class, () -> {
            userBalanceService.deductBalance(TEST_USER_ID, largeAmount);
        });

        // 변경되지 않아야 함
        assertEquals(INITIAL_BALANCE, testUser.getBalance());
        verify(userRepository).findByIdForUpdate(TEST_USER_ID);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("잔액 차감 - 실패 (사용자 미존재)")
    void testDeductBalance_UserNotFound() {
        // Given: 사용자 미존재
        when(userRepository.findByIdForUpdate(TEST_USER_ID))
                .thenReturn(Optional.empty());

        // When & Then: UserNotFoundException 발생
        assertThrows(UserNotFoundException.class, () -> {
            userBalanceService.deductBalance(TEST_USER_ID, DEDUCT_AMOUNT);
        });

        verify(userRepository).findByIdForUpdate(TEST_USER_ID);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("잔액 차감 - 실패 (0 이하의 금액)")
    void testDeductBalance_InvalidAmount() {
        // Given
        when(userRepository.findByIdForUpdate(TEST_USER_ID))
                .thenReturn(Optional.of(testUser));

        // When & Then: IllegalArgumentException 발생
        assertThrows(IllegalArgumentException.class, () -> {
            userBalanceService.deductBalance(TEST_USER_ID, 0L);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            userBalanceService.deductBalance(TEST_USER_ID, -1000L);
        });
    }

    // ========== 잔액 충전 (chargeBalance) ==========

    @Test
    @DisplayName("잔액 충전 - 성공")
    void testChargeBalance_Success() {
        // Given
        when(userRepository.findByIdForUpdate(TEST_USER_ID))
                .thenReturn(Optional.of(testUser));

        // When
        User result = userBalanceService.chargeBalance(TEST_USER_ID, CHARGE_AMOUNT);

        // Then
        assertNotNull(result);
        assertEquals(INITIAL_BALANCE + CHARGE_AMOUNT, testUser.getBalance());
        verify(userRepository).findByIdForUpdate(TEST_USER_ID);
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("잔액 충전 - 실패 (0 이하의 금액)")
    void testChargeBalance_InvalidAmount() {
        // Given
        when(userRepository.findByIdForUpdate(TEST_USER_ID))
                .thenReturn(Optional.of(testUser));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            userBalanceService.chargeBalance(TEST_USER_ID, 0L);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            userBalanceService.chargeBalance(TEST_USER_ID, -1000L);
        });
    }

    @Test
    @DisplayName("잔액 충전 - 실패 (사용자 미존재)")
    void testChargeBalance_UserNotFound() {
        // Given
        when(userRepository.findByIdForUpdate(TEST_USER_ID))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(UserNotFoundException.class, () -> {
            userBalanceService.chargeBalance(TEST_USER_ID, CHARGE_AMOUNT);
        });

        verify(userRepository).findByIdForUpdate(TEST_USER_ID);
        verify(userRepository, never()).save(any());
    }

    // ========== 잔액 환불 (refundBalance) ==========

    @Test
    @DisplayName("잔액 환불 - 성공")
    void testRefundBalance_Success() {
        // Given: 사용자의 초기 잔액을 0으로 설정
        testUser.setBalance(0L);
        when(userRepository.findByIdForUpdate(TEST_USER_ID))
                .thenReturn(Optional.of(testUser));

        // When
        User result = userBalanceService.refundBalance(TEST_USER_ID, CHARGE_AMOUNT);

        // Then
        assertNotNull(result);
        assertEquals(CHARGE_AMOUNT, testUser.getBalance());
        verify(userRepository).findByIdForUpdate(TEST_USER_ID);
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("잔액 환불 - 실패 (0 이하의 금액)")
    void testRefundBalance_InvalidAmount() {
        // Given
        when(userRepository.findByIdForUpdate(TEST_USER_ID))
                .thenReturn(Optional.of(testUser));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            userBalanceService.refundBalance(TEST_USER_ID, 0L);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            userBalanceService.refundBalance(TEST_USER_ID, -1000L);
        });
    }

    @Test
    @DisplayName("잔액 환불 - 실패 (사용자 미존재)")
    void testRefundBalance_UserNotFound() {
        // Given
        when(userRepository.findByIdForUpdate(TEST_USER_ID))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(UserNotFoundException.class, () -> {
            userBalanceService.refundBalance(TEST_USER_ID, CHARGE_AMOUNT);
        });

        verify(userRepository).findByIdForUpdate(TEST_USER_ID);
        verify(userRepository, never()).save(any());
    }

    // ========== 분산락 관련 메모 ==========

    /**
     * @DistributedLock 검증 방법:
     *
     * 1. 코드 레벨 검증:
     *    - UserBalanceService의 메서드에 @DistributedLock 어노테이션 확인
     *    - key 패턴 확인: "balance:#p0"
     *
     * 2. 로그 기반 검증:
     *    - 실행 시 다음 로그 확인:
     *      [DistributedLock] 락 획득 성공 - key: balance:1
     *      [UserBalanceService] 잔액 차감 완료: userId=1, ...
     *      [DistributedLock] 락 해제 - key: balance:1
     *
     * 3. 동시성 검증:
     *    - 두 개의 동시 요청이 순차 실행되는지 확인
     *    - Redis MONITOR로 SET ... NX 명령 확인
     *
     * 4. RuntimeException 검증:
     *    - 락 획득 실패 시 RuntimeException 발생 확인
     *    - DistributedLockAop에서 예외 처리 확인
     */
}
