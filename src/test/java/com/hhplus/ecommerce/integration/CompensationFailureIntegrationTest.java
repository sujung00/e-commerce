package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.alert.AlertService;
import com.hhplus.ecommerce.application.order.OrderSagaService;
import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import com.hhplus.ecommerce.application.order.saga.CompensationDLQ;
import com.hhplus.ecommerce.application.order.saga.FailedCompensation;
import com.hhplus.ecommerce.application.order.saga.SagaContext;
import com.hhplus.ecommerce.application.order.saga.steps.CreateOrderStep;
import com.hhplus.ecommerce.application.order.saga.steps.DeductBalanceStep;
import com.hhplus.ecommerce.application.order.saga.steps.UseCouponStep;
import com.hhplus.ecommerce.common.exception.CompensationException;
import com.hhplus.ecommerce.common.exception.CriticalException;
import com.hhplus.ecommerce.common.exception.ErrorCode;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import com.hhplus.ecommerce.domain.order.FailedCompensationEntity;
import com.hhplus.ecommerce.domain.order.FailedCompensationRepository;
import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.order.OrderStatus;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 보상 트랜잭션 실패 시나리오 Integration 테스트
 *
 * 테스트 목표:
 * - TEST-001: 단일 Step 보상 실패 - CriticalException
 * - TEST-002: 캐스케이딩 보상 실패 - 다중 Step 실패
 *
 * 테스트 전략:
 * - Spy를 사용하여 Step의 compensate() 메서드에서 예외 발생 시뮬레이션
 * - 실제 DB 트랜잭션 동작 검증
 * - Mock 최소화: AlertService, Step만 Spy 사용
 *
 * ⚠️ Spring Boot 3.4+ 호환:
 * - @SpyBean (deprecated) 대신 @TestConfiguration + Mockito.spy() 사용
 * - @Primary로 실제 빈을 spy 빈으로 교체
 * - 실제 빈의 모든 기능 유지하면서 부분 스터빙 + 검증 가능
 */
@SpringBootTest
@Import(CompensationFailureIntegrationTest.SpyConfiguration.class)
@DisplayName("보상 트랜잭션 실패 시나리오 Integration 테스트")
class CompensationFailureIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OrderSagaService orderSagaService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private FailedCompensationRepository failedCompensationRepository;

    @Autowired
    private CompensationDLQ compensationDLQ;

    // ═══════════════════════════════════════════════════════════════════════
    // Spy 빈 주입 (SpyConfiguration에서 생성된 spy 객체)
    // ═══════════════════════════════════════════════════════════════════════
    @Autowired
    private AlertService alertService; // spy 객체

    @Autowired
    private DeductBalanceStep deductBalanceStep; // spy 객체

    @Autowired
    private UseCouponStep useCouponStep; // spy 객체

    @Autowired
    private CreateOrderStep createOrderStep; // spy 객체

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate newTransactionTemplate;

    /**
     * Spring Boot 3.4+ 호환 Spy 빈 설정
     *
     * @SpyBean (deprecated) 대체 방식:
     * - 실제 빈을 Mockito.spy()로 감싸서 새로운 빈 생성
     * - @Primary로 실제 빈 대신 spy 빈이 주입되도록 설정
     * - 부분 스터빙 (doThrow, doAnswer 등) + 호출 검증 (verify) 모두 가능
     *
     * 장점:
     * 1. Spring 표준 빈 재정의 메커니즘 활용
     * 2. 실제 빈의 모든 의존성 자동 주입
     * 3. deprecated API 사용 안 함
     * 4. 테스트 코드 나머지 부분은 변경 불필요
     */
    @TestConfiguration
    static class SpyConfiguration {

        /**
         * AlertService spy 빈
         * 목적: verify()를 통한 호출 횟수 검증
         */
        @Bean
        @Primary
        public AlertService alertServiceSpy(AlertService alertService) {
            return Mockito.spy(alertService);
        }

        /**
         * DeductBalanceStep spy 빈
         * 목적: compensate() 메서드에 예외 주입 (doThrow)
         */
        @Bean
        @Primary
        public DeductBalanceStep deductBalanceStepSpy(DeductBalanceStep deductBalanceStep) {
            return Mockito.spy(deductBalanceStep);
        }

        /**
         * UseCouponStep spy 빈
         * 목적: compensate() 메서드에 예외 주입 (doThrow)
         */
        @Bean
        @Primary
        public UseCouponStep useCouponStepSpy(UseCouponStep useCouponStep) {
            return Mockito.spy(useCouponStep);
        }

        /**
         * CreateOrderStep spy 빈
         * 목적: execute() 메서드에 예외 주입 (doThrow)
         */
        @Bean
        @Primary
        public CreateOrderStep createOrderStepSpy(CreateOrderStep createOrderStep) {
            return Mockito.spy(createOrderStep);
        }
    }

    @BeforeEach
    void setUp() {
        this.newTransactionTemplate = new TransactionTemplate(transactionManager);
        this.newTransactionTemplate.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );

        // Spy 초기화: 각 테스트 전에 stubbing 및 호출 기록 초기화
        Mockito.reset(alertService, deductBalanceStep, useCouponStep, createOrderStep);
    }

    /**
     * TEST-001: 단일 Step 보상 실패 - CriticalException
     *
     * 시나리오:
     * 1. DeductInventoryStep 성공 (재고 차감)
     * 2. DeductBalanceStep 성공 (포인트 차감)
     * 3. UseCouponStep 실패 → 보상 시작
     * 4. DeductBalanceStep 보상 중 CriticalException 발생
     *
     * 검증 조건:
     * - AlertService.notifyCriticalCompensationFailure() 1회 호출
     * - CompensationDLQ에 1건 저장 (DeductBalanceStep 실패)
     * - CompensationException 발생
     * - DeductInventoryStep 보상 실행 안 됨 (중단됨)
     * - DB 최종 상태: 재고 차감 유지, 포인트 차감 유지, 주문 없음
     */
    @Test
    @DisplayName("TEST-001: 단일 Step 보상 실패 - CriticalException 발생 시 보상 중단 검증")
    @DirtiesContext
    void test001_SingleStepCompensationFailure_CriticalException() throws Exception {
        // ========== Given: 테스트 데이터 준비 ==========
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long testTimestamp = System.currentTimeMillis();

        // Step 1: 사용자, 상품, 재고 준비
        long[] testData = new long[4]; // [userId, productId, optionId, initialStock]
        newTransactionTemplate.execute(status -> {
            // 사용자 생성 (충분한 포인트)
            User user = User.builder()
                    .email(String.format("test001_user_%s_%d@test.com", testId, testTimestamp))
                    .name("보상실패테스트사용자")
                    .balance(100000L) // 충분한 잔액
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
            testData[0] = user.getUserId();

            // 상품 생성
            Product product = Product.builder()
                    .productName("보상실패테스트상품_" + testId)
                    .price(10000L)
                    .totalStock(100)
                    .status("IN_STOCK")
                    .options(new ArrayList<>())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.save(product);
            testData[1] = product.getProductId();

            // 상품 옵션 생성
            ProductOption option = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("기본옵션")
                    .stock(100)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option);
            entityManager.flush();

            var savedOptions = productRepository.findOptionsByProductId(product.getProductId());
            testData[2] = savedOptions.get(0).getOptionId();
            testData[3] = savedOptions.get(0).getStock();

            System.out.println("[TEST-001 Given] 테스트 데이터 준비 완료");
            System.out.println("  - userId: " + testData[0]);
            System.out.println("  - productId: " + testData[1]);
            System.out.println("  - optionId: " + testData[2]);
            System.out.println("  - initialStock: " + testData[3]);
            return null;
        });

        long userId = testData[0];
        long productId = testData[1];
        long optionId = testData[2];
        long initialStock = testData[3];

        // Step 2: DeductBalanceStep 보상 시 CriticalException 발생하도록 Stubbing
        doThrow(new CriticalException(
                ErrorCode.CRITICAL_COMPENSATION_FAILURE,
                "TEST-001: DeductBalanceStep 보상 중 Critical 오류 발생"
        )).when(deductBalanceStep).compensate(any(SagaContext.class));

        // ========== When: UseCouponStep 실패로 보상 트랜잭션 시작 ==========
        List<OrderItemDto> orderItems = List.of(
                new OrderItemDto(productId, optionId, 10)
        );
        Long invalidCouponId = 99999L; // 존재하지 않는 쿠폰 ID → UseCouponStep 실패
        Long finalAmount = 10000L; // 10개 * 10000원

        System.out.println("[TEST-001 When] 주문 생성 시도 (UseCouponStep 실패 예상)");

        CompensationException compensationException = assertThrows(CompensationException.class, () -> {
            orderSagaService.createOrderWithPayment(userId, orderItems, invalidCouponId, finalAmount);
        });

        System.out.println("[TEST-001 When] CompensationException 발생 확인: " + compensationException.getMessage());

        // ========== Then: 검증 ==========

        // 검증 1: CompensationException 발생
        assertNotNull(compensationException, "CompensationException이 발생해야 함");
        assertTrue(compensationException.getMessage().contains("Critical"),
                "CompensationException 메시지에 'Critical'이 포함되어야 함");
        System.out.println("[TEST-001 Then-1] ✅ CompensationException 발생 검증 완료");

        // 검증 2: AlertService.notifyCriticalCompensationFailure() 1회 호출
        verify(alertService, times(1))
                .notifyCriticalCompensationFailure(anyLong(), eq("DeductBalanceStep"));
        System.out.println("[TEST-001 Then-2] ✅ AlertService.notifyCriticalCompensationFailure() 호출 검증 완료");

        // 검증 3: CompensationDLQ에 1건 저장
        newTransactionTemplate.execute(status -> {
            List<FailedCompensation> failedCompensations = compensationDLQ.getAllFailed();

            // DLQ에 1건 이상 저장되어 있어야 함 (DeductBalanceStep 실패)
            assertFalse(failedCompensations.isEmpty(),
                    "CompensationDLQ에 최소 1건 저장되어야 함 (실제: " + failedCompensations.size() + "건)");

            // DeductBalanceStep 실패 기록 확인
            boolean hasDeductBalanceFailure = failedCompensations.stream()
                    .anyMatch(fc -> "DeductBalanceStep".equals(fc.getStepName()));
            assertTrue(hasDeductBalanceFailure,
                    "CompensationDLQ에 DeductBalanceStep 실패 기록이 있어야 함");

            System.out.println("[TEST-001 Then-3] ✅ CompensationDLQ 저장 검증 완료 (총 " + failedCompensations.size() + "건)");
            return null;
        });

        // 검증 4: DB 최종 상태 확인
        newTransactionTemplate.execute(status -> {
            // 재고 확인: 차감 유지 (보상 실행 안 됨)
            ProductOption option = productRepository.findOptionById(optionId)
                    .orElseThrow(() -> new AssertionError("옵션을 찾을 수 없음"));

            long finalStock = option.getStock();
            long expectedStock = initialStock - 10; // DeductInventoryStep 성공했으나 보상 실행 안 됨

            assertEquals(expectedStock, finalStock,
                    String.format("재고가 차감 상태로 유지되어야 함 (초기: %d, 예상: %d, 실제: %d)",
                            initialStock, expectedStock, finalStock));
            System.out.println("[TEST-001 Then-4a] ✅ 재고 차감 유지 검증 완료: " + finalStock);

            // 포인트 확인: 차감 유지 (보상 실패)
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AssertionError("사용자를 찾을 수 없음"));

            long finalBalance = user.getBalance();
            long expectedBalance = 100000L - 10000L; // DeductBalanceStep 성공했으나 보상 실패

            assertEquals(expectedBalance, finalBalance,
                    String.format("포인트가 차감 상태로 유지되어야 함 (예상: %d, 실제: %d)",
                            expectedBalance, finalBalance));
            System.out.println("[TEST-001 Then-4b] ✅ 포인트 차감 유지 검증 완료: " + finalBalance);

            // 주문 확인: 생성 안 됨
            List<Order> orders = orderRepository.findByUserId(userId, 0, 100);
            assertTrue(orders.isEmpty() || orders.stream().noneMatch(o -> o.getOrderStatus() == OrderStatus.PENDING),
                    "주문이 생성되지 않았거나 PENDING 상태가 아니어야 함");
            System.out.println("[TEST-001 Then-4c] ✅ 주문 미생성 검증 완료");

            return null;
        });

        System.out.println("[TEST-001] ✅ 테스트 완료: 모든 검증 통과");
    }

    /**
     * TEST-002: 캐스케이딩 보상 실패 - 다중 Step 실패
     *
     * 시나리오:
     * 1. DeductInventoryStep, DeductBalanceStep, UseCouponStep 성공
     * 2. CreateOrderStep 실패 → 보상 시작 (LIFO)
     * 3. UseCouponStep 보상 실패 (일반 Exception - Best Effort 계속)
     * 4. DeductBalanceStep 보상 실패 (일반 Exception - Best Effort 계속)
     * 5. DeductInventoryStep 보상 성공 (재고 복구)
     *
     * 검증 조건:
     * - CompensationDLQ에 2건 저장 (UseCouponStep, DeductBalanceStep)
     * - 주문 생성 안 됨 (CreateOrderStep 실패)
     * - 쿠폰 상태 = USED (복구 실패)
     * - 포인트 차감 유지 (환불 실패)
     * - 재고 복구됨 (DeductInventoryStep 보상 성공)
     */
    @Test
    @DisplayName("TEST-002: 캐스케이딩 보상 실패 - 다중 Step 실패 시 Best Effort 계속 진행 검증")
    @DirtiesContext
    void test002_CascadingCompensationFailure_MultipleStepFailures() throws Exception {
        // ========== Given: 테스트 데이터 준비 (모든 리소스) ==========
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long testTimestamp = System.currentTimeMillis();

        long[] testData = new long[6]; // [userId, productId, optionId, couponId, userCouponId, initialStock]
        newTransactionTemplate.execute(status -> {
            // 사용자 생성
            User user = User.builder()
                    .email(String.format("test002_user_%s_%d@test.com", testId, testTimestamp))
                    .name("다중보상실패테스트사용자")
                    .balance(100000L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
            testData[0] = user.getUserId();

            // 상품 생성
            Product product = Product.builder()
                    .productName("다중보상실패테스트상품_" + testId)
                    .price(10000L)
                    .totalStock(100)
                    .status("IN_STOCK")
                    .options(new ArrayList<>())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.save(product);
            testData[1] = product.getProductId();

            // 상품 옵션 생성
            ProductOption option = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("기본옵션")
                    .stock(100)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option);
            entityManager.flush();

            var savedOptions = productRepository.findOptionsByProductId(product.getProductId());
            testData[2] = savedOptions.get(0).getOptionId();
            testData[5] = savedOptions.get(0).getStock();

            // 쿠폰 생성
            Coupon coupon = Coupon.builder()
                    .couponName("테스트쿠폰_" + testId)
                    .discountType("FIXED")
                    .discountAmount(1000L)
                    .totalQuantity(100)
                    .remainingQty(100)
                    .validFrom(LocalDateTime.now().minusDays(1))
                    .validUntil(LocalDateTime.now().plusDays(7))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            couponRepository.save(coupon);
            testData[3] = coupon.getCouponId();

            // 사용자 쿠폰 발급
            UserCoupon userCoupon = UserCoupon.builder()
                    .userId(user.getUserId())
                    .couponId(coupon.getCouponId())
                    .status(UserCouponStatus.UNUSED)
                    .issuedAt(LocalDateTime.now())
                    .build();
            userCouponRepository.save(userCoupon);
            testData[4] = userCoupon.getUserCouponId();

            System.out.println("[TEST-002 Given] 테스트 데이터 준비 완료");
            System.out.println("  - userId: " + testData[0]);
            System.out.println("  - productId: " + testData[1]);
            System.out.println("  - optionId: " + testData[2]);
            System.out.println("  - couponId: " + testData[3]);
            System.out.println("  - userCouponId: " + testData[4]);
            System.out.println("  - initialStock: " + testData[5]);
            return null;
        });

        long userId = testData[0];
        long productId = testData[1];
        long optionId = testData[2];
        long couponId = testData[3];
        long initialStock = testData[5];

        // Step 2: CreateOrderStep execute() 실패 Stubbing
        doThrow(new RuntimeException("TEST-002: CreateOrderStep 실행 중 오류 발생 (DB 저장 실패 시뮬레이션)"))
                .when(createOrderStep).execute(any(SagaContext.class));

        // Step 3: 보상 실패 Stubbing
        // UseCouponStep 보상 실패 (일반 Exception - Best Effort)
        doThrow(new RuntimeException("TEST-002: UseCouponStep 보상 중 일반 오류 발생"))
                .when(useCouponStep).compensate(any(SagaContext.class));

        // DeductBalanceStep 보상 실패 (일반 Exception - Best Effort)
        doThrow(new RuntimeException("TEST-002: DeductBalanceStep 보상 중 일반 오류 발생"))
                .when(deductBalanceStep).compensate(any(SagaContext.class));

        // ========== When: 주문 생성 (CreateOrderStep 실패 예상) ==========
        List<OrderItemDto> orderItems = List.of(
                new OrderItemDto(productId, optionId, 10)
        );
        Long finalAmount = 10000L * 10 - 1000L; // (가격 * 수량) - 쿠폰할인

        System.out.println("[TEST-002 When] 주문 생성 시도 (CreateOrderStep 실패 예상)");

        // CreateOrderStep 실패로 인한 RuntimeException 발생 예상
        RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> {
            orderSagaService.createOrderWithPayment(userId, orderItems, couponId, finalAmount);
        });

        System.out.println("[TEST-002 When] RuntimeException 발생 확인: " + runtimeException.getMessage());

        // ========== Then: 검증 ==========

        // 검증 1: CompensationDLQ에 2건 저장 (UseCouponStep, DeductBalanceStep)
        newTransactionTemplate.execute(status -> {
            List<FailedCompensation> failedCompensations = compensationDLQ.getAllFailed();

            // DLQ에 2건 이상 저장되어 있어야 함
            assertTrue(failedCompensations.size() >= 2,
                    "CompensationDLQ에 최소 2건 저장되어야 함 (실제: " + failedCompensations.size() + "건)");

            // UseCouponStep 실패 기록 확인
            boolean hasUseCouponFailure = failedCompensations.stream()
                    .anyMatch(fc -> "UseCouponStep".equals(fc.getStepName()));
            assertTrue(hasUseCouponFailure,
                    "CompensationDLQ에 UseCouponStep 실패 기록이 있어야 함");

            // DeductBalanceStep 실패 기록 확인
            boolean hasDeductBalanceFailure = failedCompensations.stream()
                    .anyMatch(fc -> "DeductBalanceStep".equals(fc.getStepName()));
            assertTrue(hasDeductBalanceFailure,
                    "CompensationDLQ에 DeductBalanceStep 실패 기록이 있어야 함");

            System.out.println("[TEST-002 Then-1] ✅ CompensationDLQ 저장 검증 완료 (총 " + failedCompensations.size() + "건)");
            return null;
        });

        // 검증 2: DB 최종 상태 확인
        newTransactionTemplate.execute(status -> {
            // 재고 확인: 복구되어야 함 (DeductInventoryStep 보상 성공)
            ProductOption option = productRepository.findOptionById(optionId)
                    .orElseThrow(() -> new AssertionError("옵션을 찾을 수 없음"));

            long finalStock = option.getStock();
            assertEquals(initialStock, finalStock,
                    String.format("재고가 복구되어야 함 (초기: %d, 최종: %d)", initialStock, finalStock));
            System.out.println("[TEST-002 Then-2a] ✅ 재고 복구 검증 완료: " + finalStock);

            // 포인트 확인: 차감 유지 (DeductBalanceStep 보상 실패)
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AssertionError("사용자를 찾을 수 없음"));

            long finalBalance = user.getBalance();
            long expectedBalance = 100000L - 10000L + 1000L; // 쿠폰 할인 적용 후 차감

            assertEquals(expectedBalance, finalBalance,
                    String.format("포인트가 차감 상태로 유지되어야 함 (예상: %d, 실제: %d)",
                            expectedBalance, finalBalance));
            System.out.println("[TEST-002 Then-2b] ✅ 포인트 차감 유지 검증 완료: " + finalBalance);

            // 쿠폰 확인: USED 상태 유지 (UseCouponStep 보상 실패)
            UserCoupon userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId)
                    .orElseThrow(() -> new AssertionError("사용자 쿠폰을 찾을 수 없음"));

            assertEquals(UserCouponStatus.USED, userCoupon.getStatus(),
                    "쿠폰 상태가 USED로 유지되어야 함 (보상 실패)");
            System.out.println("[TEST-002 Then-2c] ✅ 쿠폰 USED 상태 유지 검증 완료");

            // 주문 확인: 생성 안 됨 (CreateOrderStep 실패)
            List<Order> orders = orderRepository.findByUserId(userId, 0, 100);
            assertTrue(orders.isEmpty() || orders.stream().noneMatch(o -> o.getOrderStatus() == OrderStatus.PENDING),
                    "주문이 생성되지 않았거나 PENDING 상태가 아니어야 함");
            System.out.println("[TEST-002 Then-2d] ✅ 주문 미생성 검증 완료");

            return null;
        });

        System.out.println("[TEST-002] ✅ 테스트 완료: 모든 검증 통과");
    }

    /**
     * TEST-003: CompensationDLQ 저장 및 조회
     *
     * 시나리오:
     * 1. FailedCompensation 생성
     * 2. CompensationDLQ.publish() 호출 (부모 트랜잭션 내)
     * 3. 부모 트랜잭션 롤백
     * 4. DLQ 데이터는 독립 트랜잭션(REQUIRES_NEW)으로 유지됨
     * 5. getFailedCompensations(orderId) 조회
     * 6. markAsResolved(orderId) 호출
     * 7. PENDING → RESOLVED 상태 전환 확인
     *
     * 검증 조건:
     * - REQUIRES_NEW 트랜잭션으로 독립 저장
     * - 부모 트랜잭션 롤백 시에도 DLQ 기록 유지
     * - FailedCompensationEntity 필드 정확성
     * - getFailedCompensations(orderId) 정상 조회
     * - PENDING → RESOLVED 상태 전환
     */
    @Test
    @DisplayName("TEST-003: CompensationDLQ 저장 및 조회 - REQUIRES_NEW 트랜잭션 독립성 검증")
    @DirtiesContext
    void test003_CompensationDLQ_SaveAndQuery() throws Exception {
        // ========== Given: 테스트 데이터 준비 ==========
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long testTimestamp = System.currentTimeMillis();

        // 테스트용 데이터 생성
        Long testUserId = 999L;
        Long testOrderId = 888L;
        String testStepName = "TestCompensationStep";
        Integer testStepOrder = 2;
        String testErrorMessage = "TEST-003: 보상 실패 테스트 메시지";
        String testStackTrace = "java.lang.RuntimeException: TEST-003\n\tat TestClass.testMethod(Test.java:123)";
        LocalDateTime testFailedAt = LocalDateTime.now();
        Integer testRetryCount = 0;
        String testContextSnapshot = "{\"orderId\":888,\"userId\":999}";

        FailedCompensation failedCompensation = FailedCompensation.builder()
                .orderId(testOrderId)
                .userId(testUserId)
                .stepName(testStepName)
                .stepOrder(testStepOrder)
                .errorMessage(testErrorMessage)
                .stackTrace(testStackTrace)
                .failedAt(testFailedAt)
                .retryCount(testRetryCount)
                .contextSnapshot(testContextSnapshot)
                .build();

        System.out.println("[TEST-003 Given] FailedCompensation 데이터 준비 완료");
        System.out.println("  - orderId: " + testOrderId);
        System.out.println("  - stepName: " + testStepName);

        // ========== When: CompensationDLQ.publish() 호출 (부모 트랜잭션 내) ==========
        // 부모 트랜잭션을 시작하고 롤백하여 REQUIRES_NEW 독립성 검증
        try {
            newTransactionTemplate.execute(status -> {
                System.out.println("[TEST-003 When] 부모 트랜잭션 시작");

                // CompensationDLQ.publish() 호출 (REQUIRES_NEW 트랜잭션)
                compensationDLQ.publish(failedCompensation);

                System.out.println("[TEST-003 When] CompensationDLQ.publish() 완료");

                // 부모 트랜잭션 강제 롤백
                status.setRollbackOnly();
                System.out.println("[TEST-003 When] 부모 트랜잭션 롤백 설정");

                return null;
            });
        } catch (Exception e) {
            // 롤백으로 인한 예외 무시
            System.out.println("[TEST-003 When] 부모 트랜잭션 롤백 완료 (예상된 동작)");
        }

        // ========== Then: 검증 ==========

        // 검증 1: 부모 트랜잭션 롤백 후에도 DLQ 데이터가 유지되는지 확인 (REQUIRES_NEW 검증)
        newTransactionTemplate.execute(status -> {
            List<FailedCompensation> savedCompensations = compensationDLQ.getFailedCompensations(testOrderId);

            assertFalse(savedCompensations.isEmpty(),
                    "부모 트랜잭션 롤백 후에도 DLQ 데이터가 유지되어야 함 (REQUIRES_NEW)");
            assertEquals(1, savedCompensations.size(),
                    "저장된 DLQ 데이터는 1건이어야 함");

            System.out.println("[TEST-003 Then-1] ✅ REQUIRES_NEW 트랜잭션 독립성 검증 완료");
            return null;
        });

        // 검증 2: FailedCompensationEntity 필드 정확성 검증
        newTransactionTemplate.execute(status -> {
            List<FailedCompensation> savedCompensations = compensationDLQ.getFailedCompensations(testOrderId);
            FailedCompensation saved = savedCompensations.get(0);

            assertEquals(testOrderId, saved.getOrderId(), "orderId가 일치해야 함");
            assertEquals(testUserId, saved.getUserId(), "userId가 일치해야 함");
            assertEquals(testStepName, saved.getStepName(), "stepName이 일치해야 함");
            assertEquals(testStepOrder, saved.getStepOrder(), "stepOrder가 일치해야 함");
            assertEquals(testErrorMessage, saved.getErrorMessage(), "errorMessage가 일치해야 함");
            assertEquals(testStackTrace, saved.getStackTrace(), "stackTrace가 일치해야 함");
            assertEquals(testRetryCount, saved.getRetryCount(), "retryCount가 일치해야 함");
            assertEquals(testContextSnapshot, saved.getContextSnapshot(), "contextSnapshot이 일치해야 함");
            assertNotNull(saved.getFailedAt(), "failedAt이 null이 아니어야 함");

            System.out.println("[TEST-003 Then-2] ✅ FailedCompensationEntity 필드 정확성 검증 완료");
            return null;
        });

        // 검증 3: getAllFailed() 조회 검증
        newTransactionTemplate.execute(status -> {
            List<FailedCompensation> allFailed = compensationDLQ.getAllFailed();

            assertFalse(allFailed.isEmpty(), "getAllFailed()는 최소 1건 이상 반환해야 함");

            boolean hasTestData = allFailed.stream()
                    .anyMatch(fc -> testOrderId.equals(fc.getOrderId()));
            assertTrue(hasTestData, "getAllFailed() 결과에 테스트 데이터가 포함되어야 함");

            System.out.println("[TEST-003 Then-3] ✅ getAllFailed() 조회 검증 완료 (총 " + allFailed.size() + "건)");
            return null;
        });

        // 검증 4: markAsResolved() 호출 및 상태 전환 확인
        newTransactionTemplate.execute(status -> {
            // PENDING → RESOLVED 상태 전환
            compensationDLQ.markAsResolved(testOrderId);

            System.out.println("[TEST-003 Then-4] markAsResolved() 호출 완료");
            return null;
        });

        // 검증 5: RESOLVED 상태로 변경되었는지 확인
        newTransactionTemplate.execute(status -> {
            // RESOLVED 상태의 데이터는 getAllFailed()에 포함되지 않아야 함
            List<FailedCompensation> pendingCompensations = compensationDLQ.getAllFailed();

            boolean hasPendingTestData = pendingCompensations.stream()
                    .anyMatch(fc -> testOrderId.equals(fc.getOrderId()));
            assertFalse(hasPendingTestData,
                    "markAsResolved() 호출 후 getAllFailed()에 해당 데이터가 없어야 함 (PENDING → RESOLVED)");

            System.out.println("[TEST-003 Then-5] ✅ PENDING → RESOLVED 상태 전환 검증 완료");
            return null;
        });

        // 검증 6: DB에서 직접 조회하여 RESOLVED 상태 확인
        newTransactionTemplate.execute(status -> {
            // Repository를 통해 직접 조회
            List<FailedCompensationEntity> entities = failedCompensationRepository.findByOrderId(testOrderId);

            assertFalse(entities.isEmpty(), "DB에 저장된 데이터가 있어야 함");
            assertEquals(1, entities.size(), "1건의 데이터가 저장되어 있어야 함");

            FailedCompensationEntity entity = entities.get(0);
            assertEquals(com.hhplus.ecommerce.domain.order.FailedCompensationStatus.RESOLVED, entity.getStatus(),
                    "상태가 RESOLVED여야 함");
            assertNotNull(entity.getResolvedAt(), "resolvedAt이 null이 아니어야 함");

            System.out.println("[TEST-003 Then-6] ✅ DB 상태 확인 완료: RESOLVED, resolvedAt=" + entity.getResolvedAt());
            return null;
        });

        System.out.println("[TEST-003] ✅ 테스트 완료: 모든 검증 통과");
    }
}
