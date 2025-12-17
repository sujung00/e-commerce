package com.hhplus.ecommerce.application.order.saga.compensation;

import com.hhplus.ecommerce.application.alert.AlertService;
import com.hhplus.ecommerce.application.order.saga.context.SagaContext;
import com.hhplus.ecommerce.common.exception.CompensationException;
import com.hhplus.ecommerce.common.exception.CriticalException;
import com.hhplus.ecommerce.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * DefaultSagaCompensationHandler 단위 테스트
 *
 * 테스트 범위:
 * 1. Critical Exception 처리
 *    - AlertService 알림 발송 검증
 *    - DLQ 발행 검증
 *    - CompensationException throw 검증
 *
 * 2. 일반 Exception 처리
 *    - DLQ 발행 검증
 *    - 예외 전파 안 됨 검증 (Best Effort)
 *
 * 3. AlertService 실패 처리
 *    - 알림 실패 시 DLQ는 발행되는지 검증
 *    - 알림 실패가 보상 처리를 중단시키지 않는지 검증
 *
 * 4. DLQ 발행 실패 처리
 *    - DLQ 발행 실패 시 예외를 전파하지 않는지 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultSagaCompensationHandler 단위 테스트")
class DefaultSagaCompensationHandlerTest {

    @Mock
    private AlertService alertService;

    @Mock
    private CompensationDLQ compensationDLQ;

    @InjectMocks
    private DefaultSagaCompensationHandler handler;

    private CompensationFailureContext context;
    private SagaContext sagaContext;

    @BeforeEach
    void setUp() {
        // SagaContext 생성 (테스트용)
        sagaContext = new SagaContext(
                1L,                    // userId
                null,                  // orderItems
                null,                  // couponId
                0L,                    // couponDiscount
                10000L,                // subtotal
                10000L                 // finalAmount
        );
        sagaContext.setOrderId(100L);
    }

    @Test
    @DisplayName("Critical Exception 처리 - AlertService 알림 + DLQ 발행 + 예외 전파")
    void handleCriticalFailure_ShouldNotifyAndPublishAndThrow() {
        // Given: Critical Exception 발생 상황
        CriticalException criticalError = new CriticalException(
                ErrorCode.CRITICAL_COMPENSATION_FAILURE,
                "Critical compensation error"
        );

        context = CompensationFailureContext.builder()
                .orderId(100L)
                .userId(1L)
                .stepName("DeductInventoryStep")
                .stepOrder(1)
                .error(criticalError)
                .sagaContext(sagaContext)
                .build();

        // When & Then: CompensationException이 throw되는지 검증
        assertThatThrownBy(() -> handler.handleFailure(context))
                .isInstanceOf(CompensationException.class)
                .hasMessageContaining("Critical compensation failed");

        // AlertService.notifyCriticalCompensationFailure() 호출 검증
        verify(alertService, times(1))
                .notifyCriticalCompensationFailure(100L, "DeductInventoryStep");

        // CompensationDLQ.publish() 호출 검증
        ArgumentCaptor<FailedCompensation> captor = ArgumentCaptor.forClass(FailedCompensation.class);
        verify(compensationDLQ, times(1)).publish(captor.capture());

        // DLQ에 발행된 내용 검증
        FailedCompensation published = captor.getValue();
        assertThat(published.getOrderId()).isEqualTo(100L);
        assertThat(published.getUserId()).isEqualTo(1L);
        assertThat(published.getStepName()).isEqualTo("DeductInventoryStep");
        assertThat(published.getStepOrder()).isEqualTo(1);
        assertThat(published.getErrorMessage()).isEqualTo("Critical compensation error");
    }

    @Test
    @DisplayName("일반 Exception 처리 - DLQ 발행만, 예외 전파 안 함 (Best Effort)")
    void handleGeneralFailure_ShouldPublishOnlyAndNotThrow() {
        // Given: 일반 Exception 발생 상황
        RuntimeException generalError = new RuntimeException("General compensation error");

        context = CompensationFailureContext.builder()
                .orderId(100L)
                .userId(1L)
                .stepName("DeductBalanceStep")
                .stepOrder(2)
                .error(generalError)
                .sagaContext(sagaContext)
                .build();

        // When: handleFailure 호출
        handler.handleFailure(context);

        // Then: AlertService는 호출되지 않음
        verify(alertService, never()).notifyCriticalCompensationFailure(anyLong(), anyString());

        // CompensationDLQ.publish()는 호출됨
        ArgumentCaptor<FailedCompensation> captor = ArgumentCaptor.forClass(FailedCompensation.class);
        verify(compensationDLQ, times(1)).publish(captor.capture());

        // DLQ에 발행된 내용 검증
        FailedCompensation published = captor.getValue();
        assertThat(published.getOrderId()).isEqualTo(100L);
        assertThat(published.getUserId()).isEqualTo(1L);
        assertThat(published.getStepName()).isEqualTo("DeductBalanceStep");
        assertThat(published.getStepOrder()).isEqualTo(2);
        assertThat(published.getErrorMessage()).isEqualTo("General compensation error");

        // 예외가 전파되지 않음 (메서드가 정상 종료됨)
        // 이 테스트가 예외 없이 통과되면 성공
    }

    @Test
    @DisplayName("Critical Exception + AlertService 실패 - DLQ는 발행되고 예외는 전파됨")
    void handleCriticalFailure_WhenAlertFails_ShouldStillPublishAndThrow() {
        // Given: Critical Exception + AlertService 실패
        CriticalException criticalError = new CriticalException(
                ErrorCode.CRITICAL_COMPENSATION_FAILURE,
                "Critical error"
        );

        context = CompensationFailureContext.builder()
                .orderId(100L)
                .userId(1L)
                .stepName("DeductInventoryStep")
                .stepOrder(1)
                .error(criticalError)
                .sagaContext(sagaContext)
                .build();

        // AlertService.notifyCriticalCompensationFailure() 실패 설정
        doThrow(new RuntimeException("Alert service failure"))
                .when(alertService)
                .notifyCriticalCompensationFailure(anyLong(), anyString());

        // When & Then: CompensationException은 여전히 throw됨
        assertThatThrownBy(() -> handler.handleFailure(context))
                .isInstanceOf(CompensationException.class);

        // AlertService 호출 검증 (실패했지만 호출은 시도됨)
        verify(alertService, times(1))
                .notifyCriticalCompensationFailure(100L, "DeductInventoryStep");

        // DLQ는 여전히 발행됨 (알림 실패에도 불구하고)
        verify(compensationDLQ, times(1)).publish(any(FailedCompensation.class));
    }

    @Test
    @DisplayName("일반 Exception + DLQ 발행 실패 - 예외를 전파하지 않음 (Best Effort)")
    void handleGeneralFailure_WhenDLQFails_ShouldNotThrow() {
        // Given: 일반 Exception + DLQ 발행 실패
        RuntimeException generalError = new RuntimeException("General error");

        context = CompensationFailureContext.builder()
                .orderId(100L)
                .userId(1L)
                .stepName("UseCouponStep")
                .stepOrder(3)
                .error(generalError)
                .sagaContext(sagaContext)
                .build();

        // CompensationDLQ.publish() 실패 설정
        doThrow(new RuntimeException("DLQ publish failure"))
                .when(compensationDLQ)
                .publish(any(FailedCompensation.class));

        // When: handleFailure 호출
        handler.handleFailure(context);

        // Then: DLQ 발행 시도는 했지만 실패함
        verify(compensationDLQ, times(1)).publish(any(FailedCompensation.class));

        // 예외가 전파되지 않음 (Best Effort)
        // 이 테스트가 예외 없이 통과되면 성공
    }

    @Test
    @DisplayName("orderId가 null인 경우 - DLQ 발행은 null orderId로 처리")
    void handleFailure_WhenOrderIdIsNull_ShouldPublishWithNullOrderId() {
        // Given: orderId가 null (주문 생성 전 실패)
        RuntimeException error = new RuntimeException("Error before order creation");

        sagaContext.setOrderId(null);

        context = CompensationFailureContext.builder()
                .orderId(null)
                .userId(1L)
                .stepName("DeductInventoryStep")
                .stepOrder(1)
                .error(error)
                .sagaContext(sagaContext)
                .build();

        // When: handleFailure 호출
        handler.handleFailure(context);

        // Then: DLQ 발행 검증 (orderId null 허용)
        ArgumentCaptor<FailedCompensation> captor = ArgumentCaptor.forClass(FailedCompensation.class);
        verify(compensationDLQ, times(1)).publish(captor.capture());

        FailedCompensation published = captor.getValue();
        assertThat(published.getOrderId()).isNull();
        assertThat(published.getUserId()).isEqualTo(1L);
        assertThat(published.getStepName()).isEqualTo("DeductInventoryStep");
    }

    @Test
    @DisplayName("CompensationFailureContext.isCriticalError() 메서드 테스트")
    void isCriticalError_ShouldReturnTrue_WhenCriticalException() {
        // Given: CriticalException
        CriticalException criticalError = new CriticalException(
                ErrorCode.CRITICAL_COMPENSATION_FAILURE,
                "Critical"
        );

        context = CompensationFailureContext.builder()
                .orderId(100L)
                .userId(1L)
                .stepName("TestStep")
                .stepOrder(1)
                .error(criticalError)
                .sagaContext(sagaContext)
                .build();

        // When & Then
        assertThat(context.isCriticalError()).isTrue();
    }

    @Test
    @DisplayName("CompensationFailureContext.isCriticalError() - 일반 Exception은 false")
    void isCriticalError_ShouldReturnFalse_WhenGeneralException() {
        // Given: 일반 Exception
        RuntimeException generalError = new RuntimeException("General");

        context = CompensationFailureContext.builder()
                .orderId(100L)
                .userId(1L)
                .stepName("TestStep")
                .stepOrder(1)
                .error(generalError)
                .sagaContext(sagaContext)
                .build();

        // When & Then
        assertThat(context.isCriticalError()).isFalse();
    }
}