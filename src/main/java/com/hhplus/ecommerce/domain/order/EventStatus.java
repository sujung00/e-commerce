package com.hhplus.ecommerce.domain.order;

/**
 * Child Transaction Event 상태
 *
 * 라이프사이클:
 * PENDING (생성)
 *   ↓
 * COMPLETED (Child TX 성공 - 보상 가능) 또는 FAILED (실패 - 보상 불필요)
 *   ↓
 * COMPENSATED (보상 완료)
 */
public enum EventStatus {
    PENDING("실행 대기"),
    COMPLETED("성공"),
    FAILED("실패"),
    COMPENSATED("보상됨");

    private final String description;

    EventStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
