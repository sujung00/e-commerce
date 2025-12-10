package com.hhplus.ecommerce.application.order.listener;

import com.hhplus.ecommerce.domain.order.event.OrderCreatedEvent;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.product.ProductNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ProductStatusEventHandler - 주문 생성 후 상품 상태 업데이트 (비동기 이벤트 핸들러)
 *
 * 역할:
 * - OrderCreatedEvent를 수신하여 상품 상태 업데이트 (품절 여부)
 * - 주문 Core Transaction과 독립적으로 비동기 실행
 * - 예외 발생 시 부모 트랜잭션에 영향 없음
 *
 * Phase 2 개선:
 * - God Transaction 해체의 일환
 * - OrderTransactionService의 updateProductStatus() 로직을 비동기 이벤트 핸들러로 분리
 * - Core TX(재고 차감, 쿠폰 처리)와 상품 상태 업데이트 분리
 * - 재고 차감은 Core TX에서 수행, 품절 여부 판단만 비동기 처리
 *
 * 비동기 처리:
 * - @Async: 별도 스레드에서 실행
 * - @EventListener: 트랜잭션과 무관하게 이벤트 발생 시 즉시 실행
 * - 주문 생성 트랜잭션이 커밋된 후 실행됨
 * - 예외 발생 시 주문 생성에 영향 없음 (이미 커밋됨)
 *
 * 처리 로직:
 * - 주문된 상품들의 재고를 재계산
 * - 모든 옵션의 재고가 0이면 '품절'로 변경
 * - 그 외의 경우 '판매중'으로 유지
 */
@Component
public class ProductStatusEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ProductStatusEventHandler.class);

    private final ProductRepository productRepository;

    public ProductStatusEventHandler(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * 주문 생성 완료 시 상품 상태 업데이트 (비동기)
     *
     * 이벤트 수신 시점:
     * - OrderTransactionService.executeTransactionalOrderInternal() 트랜잭션 커밋 후
     * - 주문 생성이 완료된 상태
     * - 별도 스레드에서 실행
     *
     * 처리 로직:
     * 1. 주문된 상품 ID 목록 추출
     * 2. 각 상품의 재고 상태 재계산 (Product.recalculateTotalStock())
     * 3. 모든 옵션 재고가 0이면 '품절', 그 외에는 '판매중'으로 변경
     * 4. DB에 저장
     *
     * 예외 처리:
     * - 예외 발생 시 로깅만 하고 주문 생성에 영향 없음
     * - 상품 상태 업데이트는 비핵심 후처리 로직
     *
     * @param event OrderCreatedEvent (orderId, userId, couponId, orderItems, productIds)
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("[ProductStatusEventHandler] 상품 상태 업데이트 시작 (비동기): orderId={}, productIds={}",
                event.getOrderId(), event.getProductIds());

        try {
            // 주문된 상품들의 상태 업데이트
            for (Long productId : event.getProductIds()) {
                try {
                    Product product = productRepository.findById(productId)
                            .orElseThrow(() -> new ProductNotFoundException(productId));

                    // Domain 메서드 호출 (Product가 상태 자동 업데이트)
                    // 모든 옵션 재고가 0이면 '품절', 그 외에는 '판매중'으로 변경
                    product.recalculateTotalStock();

                    // 저장소에 반영
                    productRepository.save(product);

                    log.info("[ProductStatusEventHandler] 상품 상태 업데이트 완료: productId={}, status={}, totalStock={}",
                            productId, product.getStatus(), product.getTotalStock());

                } catch (Exception e) {
                    // 개별 상품 처리 실패는 로깅만 하고 계속 진행
                    log.error("[ProductStatusEventHandler] 상품 상태 업데이트 실패: productId={}, error={}",
                            productId, e.getMessage());
                }
            }

            log.info("[ProductStatusEventHandler] 상품 상태 업데이트 완료 (비동기): orderId={}, productCount={}",
                    event.getOrderId(), event.getProductIds().size());

        } catch (Exception e) {
            // 전체 처리 실패는 로깅만 하고 주문에 영향 없음
            log.error("[ProductStatusEventHandler] 상품 상태 업데이트 전체 실패 (무시됨): orderId={}, error={}",
                    event.getOrderId(), e.getMessage());
        }
    }
}