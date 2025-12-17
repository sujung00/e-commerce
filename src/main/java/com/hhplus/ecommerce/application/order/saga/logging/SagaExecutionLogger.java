package com.hhplus.ecommerce.application.order.saga.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * SagaExecutionLogger - Saga 실행 흐름 추적을 위한 Aspect
 *
 * 역할:
 * - Saga 실행 단위마다 고유 sagaId 생성
 * - MDC를 통한 Saga 실행 추적 지원
 * - Saga 시작/성공/실패 로깅
 *
 * 적용 대상:
 * - OrderSagaOrchestrator.executeSaga(...) 메서드
 *
 * MDC 키:
 * - sagaId: Saga 실행 고유 식별자 (UUID)
 *
 * 로그 형식:
 * - 시작: [SAGA-START] sagaId={}
 * - 성공: [SAGA-SUCCESS] sagaId={}
 * - 실패: [SAGA-FAILED] sagaId={}, error={}
 */
@Aspect
@Component
public class SagaExecutionLogger {

    private static final Logger log = LoggerFactory.getLogger(SagaExecutionLogger.class);
    private static final String MDC_SAGA_ID_KEY = "sagaId";

    /**
     * OrderSagaOrchestrator.executeSaga(...) 메서드 실행을 감싸는 Around Advice
     *
     * 동작:
     * 1. Saga 실행 전: sagaId 생성 및 MDC 등록
     * 2. Saga 실행 중: 메서드 실행
     * 3. Saga 성공: 성공 로그 기록
     * 4. Saga 실패: 실패 로그 기록 및 예외 재발생
     * 5. 최종: MDC 정리 (finally)
     *
     * @param joinPoint Saga 실행 메서드 정보
     * @return Saga 실행 결과 (Order 객체)
     * @throws Throwable Saga 실행 중 발생한 예외
     */
    @Around("execution(* com.hhplus.ecommerce.application.order.saga.orchestration.OrderSagaOrchestrator.executeSaga(..))")
    public Object logSagaExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        // ========== Step 1: sagaId 생성 및 MDC 등록 ==========
        String sagaId = UUID.randomUUID().toString();
        MDC.put(MDC_SAGA_ID_KEY, sagaId);

        try {
            // ========== Step 2: Saga 시작 로깅 ==========
            log.info("[SAGA-START] sagaId={}", sagaId);

            // ========== Step 3: Saga 실행 ==========
            Object result = joinPoint.proceed();

            // ========== Step 4: Saga 성공 로깅 ==========
            log.info("[SAGA-SUCCESS] sagaId={}", sagaId);

            return result;

        } catch (Throwable e) {
            // ========== Step 5: Saga 실패 로깅 ==========
            log.error("[SAGA-FAILED] sagaId={}, error={}", sagaId, e.getMessage());

            // 예외 재발생 (삼키지 않음)
            throw e;

        } finally {
            // ========== Step 6: MDC 정리 ==========
            MDC.remove(MDC_SAGA_ID_KEY);
        }
    }
}
