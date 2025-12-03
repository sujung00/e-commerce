package com.hhplus.ecommerce.application.coupon.dto;

import com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * CouponIssueStatusResponse - 쿠폰 발급 상태 응답
 *
 * 역할:
 * - GET /coupons/issue/status/{requestId} 응답
 * - 비동기 쿠폰 발급의 진행 상황 조회
 * - 클라이언트 폴링용 응답 DTO
 *
 * 흐름:
 * 1. 클라이언트가 requestId로 상태 조회
 * 2. Redis에서 상태 가져오기
 * 3. 이 DTO로 응답 구성
 * 4. 클라이언트가 1초마다 폴링하며 상태 확인
 * 5. status == "COMPLETED" 또는 "FAILED" 시 폴링 중단
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CouponIssueStatusResponse {

    /**
     * 요청의 고유 ID
     * - 클라이언트가 처음 요청했을 때 받은 requestId
     */
    private String requestId;

    /**
     * 현재 상태
     * - PENDING: 처리 대기 중
     * - COMPLETED: 발급 완료
     * - FAILED: 발급 불가
     * - RETRY: 재시도 중
     * - NOT_FOUND: 요청을 찾을 수 없음
     */
    private String status;

    /**
     * 발급 결과 (상태가 COMPLETED일 때만)
     * - IssueCouponResponse 정보
     */
    private IssueCouponResponse result;

    /**
     * 에러 메시지 (상태가 FAILED 또는 RETRY일 때만)
     */
    private String errorMessage;

    /**
     * 큐에서의 대기 위치 (상태가 PENDING일 때만)
     * - 선택사항 (성능을 위해 생략 가능)
     */
    private Integer queuePosition;

    /**
     * 대기 시간 (밀리초)
     */
    private Long waitingTimeMs;

    // ===== 정적 팩토리 메서드 =====

    /**
     * PENDING 상태 응답
     */
    public static CouponIssueStatusResponse pending(String requestId) {
        return CouponIssueStatusResponse.builder()
            .requestId(requestId)
            .status("PENDING")
            .build();
    }

    /**
     * COMPLETED 상태 응답
     */
    public static CouponIssueStatusResponse completed(
            String requestId,
            IssueCouponResponse result) {
        return CouponIssueStatusResponse.builder()
            .requestId(requestId)
            .status("COMPLETED")
            .result(result)
            .build();
    }

    /**
     * FAILED 상태 응답
     */
    public static CouponIssueStatusResponse failed(
            String requestId,
            String errorMessage) {
        return CouponIssueStatusResponse.builder()
            .requestId(requestId)
            .status("FAILED")
            .errorMessage(errorMessage)
            .build();
    }

    /**
     * RETRY 상태 응답
     */
    public static CouponIssueStatusResponse retry(
            String requestId,
            String errorMessage) {
        return CouponIssueStatusResponse.builder()
            .requestId(requestId)
            .status("RETRY")
            .errorMessage(errorMessage)
            .build();
    }

    /**
     * NOT_FOUND 응답
     */
    public static CouponIssueStatusResponse notFound(String requestId) {
        return CouponIssueStatusResponse.builder()
            .requestId(requestId)
            .status("NOT_FOUND")
            .errorMessage("요청을 찾을 수 없습니다")
            .build();
    }

    /**
     * 에러 응답
     */
    public static CouponIssueStatusResponse error(String errorMessage) {
        return CouponIssueStatusResponse.builder()
            .status("ERROR")
            .errorMessage(errorMessage)
            .build();
    }
}
