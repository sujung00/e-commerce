package com.hhplus.ecommerce.application.order.saga.steps;

import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import com.hhplus.ecommerce.application.order.saga.SagaContext;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.integration.BaseIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeductBalanceStep 보상 로직 테스트
 *
 * 검증 포인트:
 * 1. SagaContext 플래그 기반 skip 로직
 * 2. 정상 보상 처리 (포인트 환불)
 * 3. 보상 실패 시 Best Effort 동작
 * 4. DB 상태 변화 검증
 */
@SpringBootTest
@DisplayName("DeductBalanceStep 보상 로직 테스트")
class DeductBalanceStepTest extends BaseIntegrationTest {

    @Autowired
    private DeductBalanceStep deductBalanceStep;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate newTransactionTemplate;

    @Autowired
    public void setTransactionManager(PlatformTransactionManager tm) {
        this.newTransactionTemplate = new TransactionTemplate(tm);
        this.newTransactionTemplate.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    @Test
    @DisplayName("[DeductBalanceStep] Forward → Backward 정상 플로우 - 포인트 차감 후 환불")
    void testCompensate_Success_RefundBalance() throws Exception {
        // Given - 테스트 데이터 준비
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long[] userIdArray = new long[1];
        long initialBalance = 100000L;
        long deductAmount = 10000L;

        // 사용자 생성
        newTransactionTemplate.execute(status -> {
            User user = User.builder()
                    .email("balance_test_" + testId + "@test.com")
                    .name("포인트테스트사용자")
                    .balance(initialBalance)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
            entityManager.flush();

            userIdArray[0] = user.getUserId();
            return null;
        });

        long userId = userIdArray[0];

        // SagaContext 생성
        SagaContext context = new SagaContext(
                userId,
                List.of(new OrderItemDto(1L, 1L, 1)),
                null, // couponId
                0L, // couponDiscount
                deductAmount, // subtotal
                deductAmount  // finalAmount
        );

        // When - Forward Flow (포인트 차감)
        deductBalanceStep.execute(context);

        // Then - Forward Flow 검증
        User afterDeduct = newTransactionTemplate.execute(status ->
            userRepository.findById(userId).orElseThrow()
        );
        assertEquals(initialBalance - deductAmount, afterDeduct.getBalance(), "포인트가 정상 차감되어야 함");
        assertTrue(context.isBalanceDeducted(), "balanceDeducted 플래그가 true여야 함");
        assertEquals(deductAmount, context.getDeductedAmount(), "차감된 금액이 기록되어야 함");

        // When - Backward Flow (포인트 환불)
        deductBalanceStep.compensate(context);

        // Then - Backward Flow 검증 (포인트 환불)
        User afterCompensate = newTransactionTemplate.execute(status ->
            userRepository.findById(userId).orElseThrow()
        );
        assertEquals(initialBalance, afterCompensate.getBalance(), "포인트가 환불되어야 함");
    }

    @Test
    @DisplayName("[DeductBalanceStep] 보상 skip - balanceDeducted=false인 경우")
    void testCompensate_Skip_WhenBalanceNotDeducted() throws Exception {
        // Given - 테스트 데이터 준비
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long[] userIdArray = new long[1];
        long initialBalance = 100000L;

        // 사용자 생성
        newTransactionTemplate.execute(status -> {
            User user = User.builder()
                    .email("balance_test_" + testId + "@test.com")
                    .name("포인트테스트사용자")
                    .balance(initialBalance)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
            entityManager.flush();

            userIdArray[0] = user.getUserId();
            return null;
        });

        long userId = userIdArray[0];

        // SagaContext 생성 (balanceDeducted=false)
        SagaContext context = new SagaContext(
                userId,
                List.of(new OrderItemDto(1L, 1L, 1)),
                null, // couponId
                0L, // couponDiscount
                10000L, // subtotal
                10000L  // finalAmount
        );

        // When - Backward Flow (보상 시도)
        deductBalanceStep.compensate(context);

        // Then - 포인트 변화 없음 (skip 되어야 함)
        User afterCompensate = newTransactionTemplate.execute(status ->
            userRepository.findById(userId).orElseThrow()
        );
        assertEquals(initialBalance, afterCompensate.getBalance(), "포인트가 변하지 않아야 함");
    }

    @Test
    @DisplayName("[DeductBalanceStep] 보상 실패 시 Best Effort - 예외 발생해도 전파하지 않음")
    void testCompensate_BestEffort_NoExceptionPropagation() throws Exception {
        // Given - 존재하지 않는 사용자 ID로 SagaContext 생성
        long nonExistentUserId = 999999L;

        SagaContext context = new SagaContext(
                nonExistentUserId,
                List.of(new OrderItemDto(1L, 1L, 1)),
                null, // couponId
                0L, // couponDiscount
                10000L, // subtotal
                10000L  // finalAmount
        );

        // 보상 플래그 설정 (환불 시도하도록)
        context.setBalanceDeducted(true);
        context.setDeductedAmount(10000L);

        // When & Then - 보상 실패해도 예외 발생하지 않음 (Best Effort)
        assertDoesNotThrow(() -> deductBalanceStep.compensate(context),
            "보상 실패해도 예외가 발생하지 않아야 함 (Best Effort)");
    }

    @Test
    @DisplayName("[DeductBalanceStep] 포인트 부족 시 Forward Flow 실패")
    void testExecute_Fail_InsufficientBalance() {
        // Given - 테스트 데이터 준비
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long[] userIdArray = new long[1];
        long initialBalance = 5000L;
        long deductAmount = 10000L; // 잔액보다 큰 금액

        // 사용자 생성
        newTransactionTemplate.execute(status -> {
            User user = User.builder()
                    .email("balance_test_" + testId + "@test.com")
                    .name("포인트테스트사용자")
                    .balance(initialBalance)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
            entityManager.flush();

            userIdArray[0] = user.getUserId();
            return null;
        });

        long userId = userIdArray[0];

        // SagaContext 생성
        SagaContext context = new SagaContext(
                userId,
                List.of(new OrderItemDto(1L, 1L, 1)),
                null, // couponId
                0L, // couponDiscount
                deductAmount, // subtotal
                deductAmount  // finalAmount
        );

        // When & Then - 포인트 부족으로 예외 발생
        assertThrows(Exception.class, () -> deductBalanceStep.execute(context),
            "포인트 부족 시 예외가 발생해야 함");

        // 포인트 변화 없음
        User afterFail = newTransactionTemplate.execute(status ->
            userRepository.findById(userId).orElseThrow()
        );
        assertEquals(initialBalance, afterFail.getBalance(), "실패 시 포인트가 변하지 않아야 함");

        // 보상 플래그 false
        assertFalse(context.isBalanceDeducted(), "실패 시 balanceDeducted 플래그가 false여야 함");
    }

    @Test
    @DisplayName("[DeductBalanceStep] 환불 후 포인트 초과 시나리오")
    void testCompensate_Success_BalanceIncrease() throws Exception {
        // Given - 테스트 데이터 준비
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long[] userIdArray = new long[1];
        long initialBalance = 50000L;
        long deductAmount = 30000L;

        // 사용자 생성
        newTransactionTemplate.execute(status -> {
            User user = User.builder()
                    .email("balance_test_" + testId + "@test.com")
                    .name("포인트테스트사용자")
                    .balance(initialBalance)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
            entityManager.flush();

            userIdArray[0] = user.getUserId();
            return null;
        });

        long userId = userIdArray[0];

        // SagaContext 생성
        SagaContext context = new SagaContext(
                userId,
                List.of(new OrderItemDto(1L, 1L, 1)),
                null, // couponId
                0L, // couponDiscount
                deductAmount, // subtotal
                deductAmount  // finalAmount
        );

        // When - Forward Flow (포인트 차감)
        deductBalanceStep.execute(context);

        // 차감 후 포인트 확인
        long balanceAfterDeduct = newTransactionTemplate.execute(status -> {
            User user = userRepository.findById(userId).orElseThrow();
            return user.getBalance();
        });
        assertEquals(initialBalance - deductAmount, balanceAfterDeduct, "포인트가 차감되어야 함");

        // When - Backward Flow (포인트 환불)
        deductBalanceStep.compensate(context);

        // Then - 환불 후 포인트 확인
        User afterCompensate = newTransactionTemplate.execute(status ->
            userRepository.findById(userId).orElseThrow()
        );
        assertEquals(initialBalance, afterCompensate.getBalance(),
            "환불 후 원래 포인트로 복구되어야 함");
    }
}
