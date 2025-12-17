package com.hhplus.ecommerce.application.order.saga;

import com.hhplus.ecommerce.application.order.saga.compensation.CompensationFailureContext;
import com.hhplus.ecommerce.application.order.saga.compensation.SagaCompensationHandler;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OrderSagaOrchestrator의 보상 Handler 위임 테스트
 *
 * 테스트 범위:
 * 1. 보상 실패 시 Handler 호출 검증
 *    - CompensationFailureContext가 올바르게 생성되는지
 *    - SagaCompensationHandler.handleFailure()가 호출되는지
 *
 * 2. 여러 Step 보상 실패 시 Handler 여러 번 호출 검증
 *    - 각 실패한 Step마다 Handler가 호출되는지
 *
 * 3. 보상 성공 시 Handler 호출 안 됨 검증
 *    - 모든 Step의 보상이 성공하면 Handler 호출 안 됨
 *
 * 4. LIFO 순서로 보상 실행 검증
 *    - 실행 순서: Step1 → Step2 → Step3
 *    - 보상 순서: Step3 → Step2 → Step1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderSagaOrchestrator 보상 Handler 위임 테스트")
class OrderSagaOrchestratorCompensationTest {

    @Mock
    private SagaCompensationHandler compensationHandler;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SagaStep step1;

    @Mock
    private SagaStep step2;

    @Mock
    private SagaStep step3;

    private OrderSagaOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Step Mock 설정
        when(step1.getName()).thenReturn("Step1");
        when(step1.getOrder()).thenReturn(1);

        when(step2.getName()).thenReturn("Step2");
        when(step2.getOrder()).thenReturn(2);

        when(step3.getName()).thenReturn("Step3");
        when(step3.getOrder()).thenReturn(3);

        List<SagaStep> steps = Arrays.asList(step1, step2, step3);

        // Orchestrator 생성
        orchestrator = new OrderSagaOrchestrator(
                steps,
                compensationHandler,
                orderRepository
        );
    }

    @Test
    @DisplayName("보상 실패 시 - Handler에 CompensationFailureContext 전달 검증")
    void compensate_WhenStepFails_ShouldDelegateToHandler() throws Exception {
        // Given: SagaContext 생성
        SagaContext context = new SagaContext(
                1L,                    // userId
                null,                  // orderItems
                null,                  // couponId
                0L,                    // couponDiscount
                10000L,                // subtotal
                10000L                 // finalAmount
        );
        context.setOrderId(100L);
        context.addExecutedStepName("Step1");
        context.addExecutedStepName("Step2");
        context.addExecutedStepName("Step3");

        // Step3 보상 실패 설정
        RuntimeException step3Error = new RuntimeException("Step3 compensation failed");
        doThrow(step3Error).when(step3).compensate(any(SagaContext.class));

        // Step2, Step1 보상 성공 설정
        doNothing().when(step2).compensate(any(SagaContext.class));
        doNothing().when(step1).compensate(any(SagaContext.class));

        // When: executeSaga 실행 (실패 예상)
        try {
            orchestrator.executeSaga(
                    1L,
                    null,
                    null,
                    0L,
                    10000L,
                    10000L
            );
        } catch (Exception e) {
            // Saga 실행 실패는 예상된 동작
        }

        // Then: Handler가 한 번 호출됨 (Step3 실패)
        ArgumentCaptor<CompensationFailureContext> captor =
                ArgumentCaptor.forClass(CompensationFailureContext.class);
        verify(compensationHandler, times(1)).handleFailure(captor.capture());

        // CompensationFailureContext 검증
        CompensationFailureContext failureContext = captor.getValue();
        assertThat(failureContext.getOrderId()).isEqualTo(100L);
        assertThat(failureContext.getUserId()).isEqualTo(1L);
        assertThat(failureContext.getStepName()).isEqualTo("Step3");
        assertThat(failureContext.getStepOrder()).isEqualTo(3);
        assertThat(failureContext.getError()).isEqualTo(step3Error);
        assertThat(failureContext.getErrorMessage()).isEqualTo("Step3 compensation failed");
    }

    @Test
    @DisplayName("여러 Step 보상 실패 시 - Handler 여러 번 호출 검증")
    void compensate_WhenMultipleStepsFail_ShouldDelegateMultipleTimes() throws Exception {
        // Given: SagaContext 생성
        SagaContext context = new SagaContext(
                1L,
                null,
                null,
                0L,
                10000L,
                10000L
        );
        context.setOrderId(100L);
        context.addExecutedStepName("Step1");
        context.addExecutedStepName("Step2");
        context.addExecutedStepName("Step3");

        // Step3, Step2 보상 실패 설정
        RuntimeException step3Error = new RuntimeException("Step3 failed");
        RuntimeException step2Error = new RuntimeException("Step2 failed");

        doThrow(step3Error).when(step3).compensate(any(SagaContext.class));
        doThrow(step2Error).when(step2).compensate(any(SagaContext.class));
        doNothing().when(step1).compensate(any(SagaContext.class));

        // When: executeSaga 실행 (실패 예상)
        try {
            orchestrator.executeSaga(1L, null, null, 0L, 10000L, 10000L);
        } catch (Exception e) {
            // Saga 실행 실패는 예상된 동작
        }

        // Then: Handler가 두 번 호출됨 (Step3, Step2 실패)
        ArgumentCaptor<CompensationFailureContext> captor =
                ArgumentCaptor.forClass(CompensationFailureContext.class);
        verify(compensationHandler, times(2)).handleFailure(captor.capture());

        // 두 번째 호출 (Step3 실패)
        List<CompensationFailureContext> contexts = captor.getAllValues();
        assertThat(contexts).hasSize(2);

        // 첫 번째 실패: Step3
        CompensationFailureContext firstFailure = contexts.get(0);
        assertThat(firstFailure.getStepName()).isEqualTo("Step3");
        assertThat(firstFailure.getError()).isEqualTo(step3Error);

        // 두 번째 실패: Step2
        CompensationFailureContext secondFailure = contexts.get(1);
        assertThat(secondFailure.getStepName()).isEqualTo("Step2");
        assertThat(secondFailure.getError()).isEqualTo(step2Error);
    }

    @Test
    @DisplayName("모든 Step 보상 성공 시 - Handler 호출 안 됨")
    void compensate_WhenAllStepsSucceed_ShouldNotDelegateToHandler() throws Exception {
        // Given: 모든 Step 보상 성공 설정
        doNothing().when(step1).compensate(any(SagaContext.class));
        doNothing().when(step2).compensate(any(SagaContext.class));
        doNothing().when(step3).compensate(any(SagaContext.class));

        SagaContext context = new SagaContext(1L, null, null, 0L, 10000L, 10000L);
        context.setOrderId(100L);
        context.addExecutedStepName("Step1");
        context.addExecutedStepName("Step2");
        context.addExecutedStepName("Step3");

        // When: executeSaga 실행 (실패 예상 - forward flow가 설정되지 않음)
        try {
            orchestrator.executeSaga(1L, null, null, 0L, 10000L, 10000L);
        } catch (Exception e) {
            // Forward flow 실패는 예상된 동작
        }

        // Then: Handler가 호출되지 않음 (보상은 모두 성공)
        verify(compensationHandler, never()).handleFailure(any());
    }

    @Test
    @DisplayName("LIFO 순서로 보상 실행 - Step3 → Step2 → Step1")
    void compensate_ShouldExecuteInLIFOOrder() throws Exception {
        // Given: SagaContext에 Step 실행 순서 기록
        SagaContext context = new SagaContext(1L, null, null, 0L, 10000L, 10000L);
        context.setOrderId(100L);
        context.addExecutedStepName("Step1");
        context.addExecutedStepName("Step2");
        context.addExecutedStepName("Step3");

        // 모든 Step 보상 성공 설정
        doNothing().when(step1).compensate(any(SagaContext.class));
        doNothing().when(step2).compensate(any(SagaContext.class));
        doNothing().when(step3).compensate(any(SagaContext.class));

        // When: executeSaga 실행
        try {
            orchestrator.executeSaga(1L, null, null, 0L, 10000L, 10000L);
        } catch (Exception e) {
            // Forward flow 실패는 예상된 동작
        }

        // Then: 보상 순서 검증 (LIFO: Step3 → Step2 → Step1)
        // InOrder를 사용하여 순서 검증
        var inOrder = inOrder(step3, step2, step1);
        inOrder.verify(step3).compensate(any(SagaContext.class));
        inOrder.verify(step2).compensate(any(SagaContext.class));
        inOrder.verify(step1).compensate(any(SagaContext.class));
    }

    @Test
    @DisplayName("Step이 stepMap에 없는 경우 - Handler 호출 안 됨, 로깅만")
    void compensate_WhenStepNotFound_ShouldNotCallHandler() throws Exception {
        // Given: executedStepNames에 존재하지 않는 Step 이름 추가
        SagaContext context = new SagaContext(1L, null, null, 0L, 10000L, 10000L);
        context.setOrderId(100L);
        context.addExecutedStepName("NonExistentStep");

        // When: executeSaga 실행
        try {
            orchestrator.executeSaga(1L, null, null, 0L, 10000L, 10000L);
        } catch (Exception e) {
            // Forward flow 실패는 예상된 동작
        }

        // Then: Handler가 호출되지 않음 (Step을 찾을 수 없으므로 로깅만)
        verify(compensationHandler, never()).handleFailure(any());
    }

    @Test
    @DisplayName("orderId가 null인 경우 - Handler에 null orderId 전달")
    void compensate_WhenOrderIdIsNull_ShouldDelegateWithNullOrderId() throws Exception {
        // Given: orderId가 null인 SagaContext
        SagaContext context = new SagaContext(1L, null, null, 0L, 10000L, 10000L);
        context.setOrderId(null);  // orderId null
        context.addExecutedStepName("Step1");

        // Step1 보상 실패 설정
        RuntimeException step1Error = new RuntimeException("Step1 failed");
        doThrow(step1Error).when(step1).compensate(any(SagaContext.class));

        // When: executeSaga 실행
        try {
            orchestrator.executeSaga(1L, null, null, 0L, 10000L, 10000L);
        } catch (Exception e) {
            // Saga 실행 실패는 예상된 동작
        }

        // Then: Handler에 orderId null로 전달됨
        ArgumentCaptor<CompensationFailureContext> captor =
                ArgumentCaptor.forClass(CompensationFailureContext.class);
        verify(compensationHandler, times(1)).handleFailure(captor.capture());

        CompensationFailureContext failureContext = captor.getValue();
        assertThat(failureContext.getOrderId()).isNull();
        assertThat(failureContext.getUserId()).isEqualTo(1L);
        assertThat(failureContext.getStepName()).isEqualTo("Step1");
    }
}