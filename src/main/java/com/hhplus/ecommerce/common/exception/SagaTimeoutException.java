package com.hhplus.ecommerce.common.exception;

/**
 * SagaTimeoutException - Saga 실행 타임아웃 예외
 *
 * 역할:
 * - Saga 워크플로우 실행 시간 초과 시 발생
 * - 타임아웃 설정 시간 내에 완료되지 않은 경우
 * - 보상 트랜잭션 트리거 및 리소스 정리 필요
 *
 * 발생 시나리오:
 * - 네트워크 지연으로 인한 외부 API 응답 지연
 * - DB 락 대기 시간 초과
 * - Step 실행 중 예상치 못한 블로킹
 * - 시스템 과부하로 인한 처리 지연
 *
 * 처리 방법:
 * - 보상 트랜잭션 실행 (이미 실행된 Step들 롤백)
 * - 부분 성공 상태 기록
 * - 관리자 알림 발송
 * - 재시도 큐에 등록 또는 DLQ로 이동
 */
public class SagaTimeoutException extends RuntimeException {

    private final long timeoutMillis;
    private final int completedSteps;
    private final int totalSteps;

    /**
     * Saga 타임아웃 예외 생성
     *
     * @param message 예외 메시지
     * @param timeoutMillis 타임아웃 설정 시간 (밀리초)
     * @param completedSteps 완료된 Step 수
     * @param totalSteps 전체 Step 수
     */
    public SagaTimeoutException(String message, long timeoutMillis, int completedSteps, int totalSteps) {
        super(message);
        this.timeoutMillis = timeoutMillis;
        this.completedSteps = completedSteps;
        this.totalSteps = totalSteps;
    }

    /**
     * Saga 타임아웃 예외 생성 (원인 예외 포함)
     *
     * @param message 예외 메시지
     * @param cause 원인 예외
     * @param timeoutMillis 타임아웃 설정 시간 (밀리초)
     * @param completedSteps 완료된 Step 수
     * @param totalSteps 전체 Step 수
     */
    public SagaTimeoutException(String message, Throwable cause, long timeoutMillis, int completedSteps, int totalSteps) {
        super(message, cause);
        this.timeoutMillis = timeoutMillis;
        this.completedSteps = completedSteps;
        this.totalSteps = totalSteps;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public int getCompletedSteps() {
        return completedSteps;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    @Override
    public String toString() {
        return String.format(
                "SagaTimeoutException: %s (timeout=%dms, completed=%d/%d steps)",
                getMessage(),
                timeoutMillis,
                completedSteps,
                totalSteps
        );
    }
}