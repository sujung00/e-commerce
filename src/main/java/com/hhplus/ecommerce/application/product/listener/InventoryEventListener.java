package com.hhplus.ecommerce.application.product.listener;

import com.hhplus.ecommerce.application.alert.AlertService;
import com.hhplus.ecommerce.domain.product.event.LowInventoryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * InventoryEventListener - 재고 이벤트 리스너
 *
 * 역할:
 * - 재고 부족 이벤트 수신 시 관리자 알림 처리
 * - 트랜잭션 커밋 후 비동기로 실행하여 주문 트랜잭션과 분리
 *
 * 트랜잭션 분리 이유:
 * - 알림 발송은 외부 I/O 작업 (이메일, SMS, Slack 등)
 * - 트랜잭션 내부에서 실행 시 성능 저하 및 트랜잭션 지연
 * - 알림 실패가 비즈니스 트랜잭션에 영향을 주지 않아야 함
 *
 * 이벤트 처리 시점: AFTER_COMMIT
 * - 트랜잭션 커밋 성공 후에만 실행
 * - 주문 실패(롤백) 시 이벤트 미발행으로 불필요한 알림 방지
 *
 * 비동기 처리:
 * - @Async로 별도 스레드에서 실행
 * - 메인 주문 트랜잭션 블로킹 방지
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventListener {

    private final AlertService alertService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLowInventory(LowInventoryEvent event) {
        log.info("[InventoryEventListener] 재고 부족 알림 - productId={}, optionId={}, stock={}, threshold={}",
            event.getProductId(), event.getOptionId(), event.getCurrentStock(), event.getThreshold());

        try {
            alertService.notifyLowInventory(
                event.getProductId(),
                event.getOptionId(),
                event.getProductName(),
                event.getOptionName(),
                event.getCurrentStock(),
                event.getThreshold()
            );

            log.info("[InventoryEventListener] 재고 부족 알림 전송 완료 - productId={}, optionId={}",
                event.getProductId(), event.getOptionId());

        } catch (Exception e) {
            log.error("[InventoryEventListener] 재고 부족 알림 실패 - productId={}, optionId={}, error={}",
                event.getProductId(), event.getOptionId(), e.getMessage(), e);
        }
    }
}