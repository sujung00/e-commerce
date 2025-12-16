package com.hhplus.ecommerce.application.order.saga.steps;

import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import com.hhplus.ecommerce.application.order.saga.SagaContext;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
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
 * UseCouponStep 보상 로직 테스트
 *
 * 검증 포인트:
 * 1. SagaContext 플래그 기반 skip 로직
 * 2. 정상 보상 처리 (쿠폰 상태 복구 USED → UNUSED)
 * 3. 쿠폰이 없는 경우 skip 로직
 * 4. DB 상태 변화 검증
 */
@SpringBootTest
@DisplayName("UseCouponStep 보상 로직 테스트")
class UseCouponStepTest extends BaseIntegrationTest {

    @Autowired
    private UseCouponStep useCouponStep;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

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
    @DisplayName("[UseCouponStep] Forward → Backward 정상 플로우 - 쿠폰 사용 후 복구")
    void testCompensate_Success_RestoreCoupon() throws Exception {
        // Given - 테스트 데이터 준비
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long[] userIdArray = new long[1];
        long[] couponIdArray = new long[1];

        // 사용자, 쿠폰, UserCoupon 생성
        newTransactionTemplate.execute(status -> {
            User user = User.builder()
                    .email("coupon_test_" + testId + "@test.com")
                    .name("쿠폰테스트사용자")
                    .balance(100000L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);

            Coupon coupon = Coupon.builder()
                    .couponName("테스트쿠폰_" + testId)
                    .discountType("FIXED_AMOUNT")
                    .discountAmount(5000L)
                    .totalQuantity(10)
                    .remainingQty(10)
                    .validFrom(LocalDateTime.now())
                    .validUntil(LocalDateTime.now().plusDays(30))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            couponRepository.save(coupon);

            UserCoupon userCoupon = UserCoupon.builder()
                    .userId(user.getUserId())
                    .couponId(coupon.getCouponId())
                    .status(UserCouponStatus.UNUSED)
                    .issuedAt(LocalDateTime.now())
                    .build();
            userCouponRepository.save(userCoupon);
            entityManager.flush();

            userIdArray[0] = user.getUserId();
            couponIdArray[0] = coupon.getCouponId();
            return null;
        });

        long userId = userIdArray[0];
        long couponId = couponIdArray[0];

        // SagaContext 생성
        SagaContext context = new SagaContext(
                userId,
                List.of(new OrderItemDto(1L, 1L, 1)),
                couponId, // couponId
                5000L, // couponDiscount
                10000L, // subtotal
                5000L  // finalAmount
        );

        // When - Forward Flow (쿠폰 사용)
        useCouponStep.execute(context);

        // Then - Forward Flow 검증
        UserCoupon afterUse = newTransactionTemplate.execute(status ->
            userCouponRepository.findByUserIdAndCouponIdForUpdate(userId, couponId).orElseThrow()
        );
        assertEquals(UserCouponStatus.USED, afterUse.getStatus(), "쿠폰 상태가 USED여야 함");
        assertNotNull(afterUse.getUsedAt(), "usedAt이 설정되어야 함");
        assertTrue(context.isCouponUsed(), "couponUsed 플래그가 true여야 함");
        assertEquals(couponId, context.getUsedCouponId(), "사용된 쿠폰 ID가 기록되어야 함");

        // When - Backward Flow (쿠폰 복구)
        useCouponStep.compensate(context);

        // Then - Backward Flow 검증 (쿠폰 상태 복구)
        UserCoupon afterCompensate = newTransactionTemplate.execute(status ->
            userCouponRepository.findByUserIdAndCouponIdForUpdate(userId, couponId).orElseThrow()
        );
        assertEquals(UserCouponStatus.UNUSED, afterCompensate.getStatus(), "쿠폰 상태가 UNUSED로 복구되어야 함");
        assertNull(afterCompensate.getUsedAt(), "usedAt이 null로 복구되어야 함");
    }

    @Test
    @DisplayName("[UseCouponStep] Forward skip - couponId=null인 경우")
    void testExecute_Skip_WhenCouponIdNull() throws Exception {
        // Given - 쿠폰 없이 SagaContext 생성
        SagaContext context = new SagaContext(
                1L, // userId
                List.of(new OrderItemDto(1L, 1L, 1)),
                null, // couponId (null)
                0L, // couponDiscount
                10000L, // subtotal
                10000L  // finalAmount
        );

        // When - Forward Flow (쿠폰 사용 시도)
        useCouponStep.execute(context);

        // Then - skip 되어야 함
        assertFalse(context.isCouponUsed(), "couponUsed 플래그가 false여야 함");
        assertNull(context.getUsedCouponId(), "usedCouponId가 null이어야 함");
    }

    @Test
    @DisplayName("[UseCouponStep] 보상 skip - couponUsed=false인 경우")
    void testCompensate_Skip_WhenCouponNotUsed() throws Exception {
        // Given - 테스트 데이터 준비
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long[] userIdArray = new long[1];
        long[] couponIdArray = new long[1];

        // 사용자, 쿠폰, UserCoupon 생성
        newTransactionTemplate.execute(status -> {
            User user = User.builder()
                    .email("coupon_test_" + testId + "@test.com")
                    .name("쿠폰테스트사용자")
                    .balance(100000L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);

            Coupon coupon = Coupon.builder()
                    .couponName("테스트쿠폰_" + testId)
                    .discountType("FIXED_AMOUNT")
                    .discountAmount(5000L)
                    .totalQuantity(10)
                    .remainingQty(10)
                    .validFrom(LocalDateTime.now())
                    .validUntil(LocalDateTime.now().plusDays(30))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            couponRepository.save(coupon);

            UserCoupon userCoupon = UserCoupon.builder()
                    .userId(user.getUserId())
                    .couponId(coupon.getCouponId())
                    .status(UserCouponStatus.UNUSED)
                    .issuedAt(LocalDateTime.now())
                    .build();
            userCouponRepository.save(userCoupon);
            entityManager.flush();

            userIdArray[0] = user.getUserId();
            couponIdArray[0] = coupon.getCouponId();
            return null;
        });

        long userId = userIdArray[0];
        long couponId = couponIdArray[0];

        // SagaContext 생성 (couponUsed=false)
        SagaContext context = new SagaContext(
                userId,
                List.of(new OrderItemDto(1L, 1L, 1)),
                couponId, // couponId
                5000L, // couponDiscount
                10000L, // subtotal
                5000L  // finalAmount
        );

        // When - Backward Flow (보상 시도)
        useCouponStep.compensate(context);

        // Then - 쿠폰 상태 변화 없음 (skip 되어야 함)
        UserCoupon afterCompensate = newTransactionTemplate.execute(status ->
            userCouponRepository.findByUserIdAndCouponIdForUpdate(userId, couponId).orElseThrow()
        );
        assertEquals(UserCouponStatus.UNUSED, afterCompensate.getStatus(), "쿠폰 상태가 변하지 않아야 함");
    }

    @Test
    @DisplayName("[UseCouponStep] 보상 실패 시 Best Effort - 예외 발생해도 전파하지 않음")
    void testCompensate_BestEffort_NoExceptionPropagation() throws Exception {
        // Given - 존재하지 않는 쿠폰으로 SagaContext 생성
        long nonExistentCouponId = 999999L;

        SagaContext context = new SagaContext(
                1L, // userId
                List.of(new OrderItemDto(1L, 1L, 1)),
                nonExistentCouponId, // couponId
                5000L, // couponDiscount
                10000L, // subtotal
                5000L  // finalAmount
        );

        // 보상 플래그 설정 (복구 시도하도록)
        context.setCouponUsed(true);
        context.setUsedCouponId(nonExistentCouponId);

        // When & Then - 보상 실패해도 예외 발생하지 않음 (Best Effort)
        assertDoesNotThrow(() -> useCouponStep.compensate(context),
            "보상 실패해도 예외가 발생하지 않아야 함 (Best Effort)");
    }

    @Test
    @DisplayName("[UseCouponStep] 쿠폰이 UNUSED가 아닌 경우 Forward Flow 실패")
    void testExecute_Fail_CouponNotUnused() {
        // Given - 테스트 데이터 준비
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long[] userIdArray = new long[1];
        long[] couponIdArray = new long[1];

        // 사용자, 쿠폰, UserCoupon 생성 (이미 USED 상태)
        newTransactionTemplate.execute(status -> {
            User user = User.builder()
                    .email("coupon_test_" + testId + "@test.com")
                    .name("쿠폰테스트사용자")
                    .balance(100000L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);

            Coupon coupon = Coupon.builder()
                    .couponName("테스트쿠폰_" + testId)
                    .discountType("FIXED_AMOUNT")
                    .discountAmount(5000L)
                    .totalQuantity(10)
                    .remainingQty(10)
                    .validFrom(LocalDateTime.now())
                    .validUntil(LocalDateTime.now().plusDays(30))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            couponRepository.save(coupon);

            UserCoupon userCoupon = UserCoupon.builder()
                    .userId(user.getUserId())
                    .couponId(coupon.getCouponId())
                    .status(UserCouponStatus.USED) // 이미 USED 상태
                    .issuedAt(LocalDateTime.now())
                    .usedAt(LocalDateTime.now())
                    .build();
            userCouponRepository.save(userCoupon);
            entityManager.flush();

            userIdArray[0] = user.getUserId();
            couponIdArray[0] = coupon.getCouponId();
            return null;
        });

        long userId = userIdArray[0];
        long couponId = couponIdArray[0];

        // SagaContext 생성
        SagaContext context = new SagaContext(
                userId,
                List.of(new OrderItemDto(1L, 1L, 1)),
                couponId, // couponId
                5000L, // couponDiscount
                10000L, // subtotal
                5000L  // finalAmount
        );

        // When & Then - 쿠폰이 UNUSED가 아니므로 예외 발생
        assertThrows(IllegalArgumentException.class, () -> useCouponStep.execute(context),
            "쿠폰이 UNUSED가 아니면 예외가 발생해야 함");

        // 보상 플래그 false
        assertFalse(context.isCouponUsed(), "실패 시 couponUsed 플래그가 false여야 함");
    }

    @Test
    @DisplayName("[UseCouponStep] 존재하지 않는 쿠폰으로 Forward Flow 실패")
    void testExecute_Fail_CouponNotFound() {
        // Given - 존재하지 않는 쿠폰으로 SagaContext 생성
        long nonExistentCouponId = 999999L;

        SagaContext context = new SagaContext(
                1L, // userId
                List.of(new OrderItemDto(1L, 1L, 1)),
                nonExistentCouponId, // couponId
                5000L, // couponDiscount
                10000L, // subtotal
                5000L  // finalAmount
        );

        // When & Then - 쿠폰을 찾을 수 없으므로 예외 발생
        assertThrows(IllegalArgumentException.class, () -> useCouponStep.execute(context),
            "쿠폰을 찾을 수 없으면 예외가 발생해야 함");

        // 보상 플래그 false
        assertFalse(context.isCouponUsed(), "실패 시 couponUsed 플래그가 false여야 함");
    }
}
