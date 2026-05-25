package com.hhplus.ecommerce.unit.application.alert;

import com.hhplus.ecommerce.application.alert.AlertService;
import com.hhplus.ecommerce.domain.order.Outbox;
import com.hhplus.ecommerce.infrastructure.alert.AlertChannel;
import com.hhplus.ecommerce.unit.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AlertService 단위 테스트
 * - 9개 알림 메서드가 AlertChannel.send()를 올바른 level로 호출하는지 검증
 */
@DisplayName("[Unit] AlertService - 9개 알림 메서드 검증")
class AlertServiceTest extends BaseUnitTest {

    @Mock
    private AlertChannel alertChannel;

    @InjectMocks
    private AlertService alertService;

    @Test
    @DisplayName("결제 실패 알림: ERROR level로 send 호출")
    void notifyPaymentFailure_sendsErrorAlert() {
        alertService.notifyPaymentFailure(1L, 2L, 50000L, "타임아웃");

        verify(alertChannel).send(eq("ERROR"), anyString(), anyString());
    }

    @Test
    @DisplayName("보상 완료 알림: WARN level로 send 호출")
    void notifyCompensationComplete_sendsWarnAlert() {
        alertService.notifyCompensationComplete(1L, 2L, 50000L);

        verify(alertChannel).send(eq("WARN"), anyString(), anyString());
    }

    @Test
    @DisplayName("보상 실패 알림: ERROR level로 send 호출")
    void notifyCompensationFailure_sendsErrorAlert() {
        alertService.notifyCompensationFailure(1L, 2L, "DB 오류");

        verify(alertChannel).send(eq("ERROR"), anyString(), anyString());
    }

    @Test
    @DisplayName("결제 성공 알림: INFO level로 send 호출")
    void notifyPaymentSuccess_sendsInfoAlert() {
        alertService.notifyPaymentSuccess(1L, 2L, 50000L);

        verify(alertChannel).send(eq("INFO"), anyString(), anyString());
    }

    @Test
    @DisplayName("결제 부분 성공 알림: WARN level로 send 호출")
    void notifyPartialPaymentSuccess_sendsWarnAlert() {
        alertService.notifyPartialPaymentSuccess(1L, 2L, 30000L, "3D Secure 실패");

        verify(alertChannel).send(eq("WARN"), anyString(), anyString());
    }

    @Test
    @DisplayName("Outbox 발행 실패 알림: ERROR level로 send 호출")
    void notifyOutboxFailure_sendsErrorAlert() {
        Outbox outbox = Outbox.builder()
                .messageId(100L)
                .orderId(1L)
                .userId(2L)
                .messageType("ORDER_COMPLETED")
                .retryCount(3)
                .build();

        alertService.notifyOutboxFailure(outbox);

        verify(alertChannel).send(eq("ERROR"), anyString(), anyString());
    }

    @Test
    @DisplayName("재고 부족 알림: WARN level로 send 호출")
    void notifyLowInventory_sendsWarnAlert() {
        alertService.notifyLowInventory(10L, 20L, "상품A", "옵션B", 3, 10);

        verify(alertChannel).send(eq("WARN"), anyString(), anyString());
    }

    @Test
    @DisplayName("중요 보상 실패 알림: ERROR level로 send 호출")
    void notifyCriticalCompensationFailure_sendsErrorAlert() {
        alertService.notifyCriticalCompensationFailure(1L, "DeductInventoryStep");

        verify(alertChannel).send(eq("ERROR"), anyString(), anyString());
    }

    @Test
    @DisplayName("네트워크 파티션 알림: ERROR level로 send 호출")
    void notifyNetworkPartition_sendsErrorAlert() {
        alertService.notifyNetworkPartition(2L, 1L, "재고 차감 성공, 잔액 차감 실패");

        verify(alertChannel).send(eq("ERROR"), anyString(), anyString());
    }
}
