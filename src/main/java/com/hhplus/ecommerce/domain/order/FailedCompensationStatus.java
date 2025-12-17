package com.hhplus.ecommerce.domain.order;

/**
 * FailedCompensationStatus - 보상 실패 처리 상태
 *
 * PENDING: 대기 중 (미처리)
 * RESOLVED: 해결됨 (수동 처리 완료)
 * ABANDONED: 폐기됨 (재처리 불가)
 */
public enum FailedCompensationStatus {
    /**
     * 대기 중 (미처리) - DLQ에 적재된 상태
     */
    PENDING,

    /**
     * 해결됨 (수동 처리 완료) - 관리자가 수동으로 처리 완료
     */
    RESOLVED,

    /**
     * 폐기됨 (재처리 불가) - 재시도 불가능한 상태
     */
    ABANDONED
}