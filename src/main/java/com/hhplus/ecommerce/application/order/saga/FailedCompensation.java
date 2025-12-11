package com.hhplus.ecommerce.application.order.saga;

import lombok.*;

import java.time.LocalDateTime;

/**
 * FailedCompensation - 실패한 보상 트랜잭션 메타데이터
 *
 * 역할:
 * - 보상 실패 정보를 DLQ(Dead Letter Queue)로 발행하기 위한 DTO
 * - 수동 재처리를 위한 모든 컨텍스트 정보 보관
 * - 실패 원인, Step 정보, 재시도 횟수 등 추적
 *
 * 저장 정보:
 * - orderId: 주문 ID (식별자)
 * - userId: 사용자 ID
 * - stepName: 실패한 Step 이름 (예: DeductInventoryStep)
 * - stepOrder: Step 실행 순서 (1~4)
 * - errorMessage: 실패 원인
 * - failedAt: 실패 시각
 * - retryCount: 재시도 횟수 (현재는 0, 향후 재시도 로직 추가 가능)
 * - context: Saga 실행 컨텍스트 (보상 재실행에 필요한 데이터)
 *
 * 사용 흐름:
 * 1. OrderSagaOrchestrator.compensate() 중 예외 발생
 * 2. FailedCompensation 객체 생성
 * 3. CompensationDLQ.publish()로 DLQ 저장
 * 4. 관리자가 DLQ 조회하여 수동 재처리
 *
 * 향후 확장:
 * - Kafka DLQ Topic으로 발행
 * - DB 테이블 저장 (failed_compensations)
 * - 자동 재시도 로직 추가
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class FailedCompensation {

    /**
     * 주문 ID
     */
    private Long orderId;

    /**
     * 사용자 ID
     */
    private Long userId;

    /**
     * 실패한 Step 이름 (예: DeductInventoryStep)
     */
    private String stepName;

    /**
     * Step 실행 순서 (1~4)
     */
    private Integer stepOrder;

    /**
     * 실패 원인 메시지
     */
    private String errorMessage;

    /**
     * 예외 스택 트레이스 (디버깅용)
     */
    private String stackTrace;

    /**
     * 실패 시각
     */
    @Builder.Default
    private LocalDateTime failedAt = LocalDateTime.now();

    /**
     * 재시도 횟수 (현재는 0, 향후 재시도 로직 추가 시 사용)
     */
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * Saga Context 정보 (JSON 직렬화)
     * 보상 재실행에 필요한 모든 데이터
     */
    private String contextSnapshot;

    /**
     * FailedCompensation 생성 팩토리 메서드
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param step 실패한 SagaStep
     * @param error 예외 객체
     * @param context Saga 실행 컨텍스트
     * @return FailedCompensation 객체
     */
    public static FailedCompensation from(Long orderId,
                                          Long userId,
                                          SagaStep step,
                                          Exception error,
                                          SagaContext context) {
        return FailedCompensation.builder()
                .orderId(orderId)
                .userId(userId)
                .stepName(step.getName())
                .stepOrder(step.getOrder())
                .errorMessage(error.getMessage())
                .stackTrace(getStackTraceAsString(error))
                .failedAt(LocalDateTime.now())
                .retryCount(0)
                .contextSnapshot(context.toString())
                .build();
    }

    /**
     * 스택 트레이스를 String으로 변환
     *
     * @param error 예외 객체
     * @return 스택 트레이스 문자열
     */
    private static String getStackTraceAsString(Exception error) {
        if (error == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(error.getClass().getName()).append(": ").append(error.getMessage()).append("\n");

        for (StackTraceElement element : error.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
            // 스택 트레이스 길이 제한 (너무 길면 로그/DB 부담)
            if (sb.length() > 2000) {
                sb.append("\t... (truncated)");
                break;
            }
        }

        return sb.toString();
    }
}