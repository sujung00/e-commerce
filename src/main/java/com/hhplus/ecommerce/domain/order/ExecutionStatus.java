package com.hhplus.ecommerce.domain.order;

/**
 * Executed Child Transaction 상태
 *
 * 멱등성 토큰 기반 재시도 안전성을 위한 상태
 *
 * 라이프사이클:
 * PENDING (실행 시작 - 아직 완료 안 됨)
 *   ↓
 * COMPLETED (성공 - 중복 실행 방지) 또는 FAILED (실패 - 재시도 가능)
 */
public enum ExecutionStatus {
    PENDING("아직 실행 안 됨"),
    COMPLETED("성공"),
    FAILED("실패");

    private final String description;

    ExecutionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
