package com.hhplus.ecommerce.presentation.coupon.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * CouponIssueAsyncResponse - 비동기 쿠폰 발급 응답
 *
 * 역할:
 * - POST /coupons/issue/async 응답
 * - 비동기 요청의 requestId 반환
 * - 클라이언트가 이 requestId로 상태를 폴링
 *
 * 흐름:
 * 1. 클라이언트가 POST /coupons/issue/async
 * 2. 서버가 requestId 반환 (202 Accepted)
 * 3. 클라이언트가 GET /coupons/issue/status/{requestId} 폴링
 * 4. 상태가 COMPLETED 또는 FAILED이면 폴링 중단
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponIssueAsyncResponse {

    /**
     * 요청의 고유 ID
     * - UUID로 생성
     * - 클라이언트가 상태 조회 시 사용
     */
    private String requestId;

    /**
     * 요청 상태
     * - PENDING: 처리 대기 중
     * - ERROR: 요청 처리 중 오류
     */
    private String status;

    /**
     * 안내 메시지
     * - "쿠폰 발급 요청이 접수되었습니다. requestId로 상태를 확인하세요."
     * - "쿠폰을 찾을 수 없습니다"
     * - 기타 에러 메시지
     */
    private String message;

    /**
     * 성공 응답 (PENDING)
     */
    public static CouponIssueAsyncResponse pending(String requestId, String message) {
        return CouponIssueAsyncResponse.builder()
                .requestId(requestId)
                .status("PENDING")
                .message(message)
                .build();
    }

    /**
     * 에러 응답
     */
    public static CouponIssueAsyncResponse error(String errorMessage) {
        return CouponIssueAsyncResponse.builder()
                .status("ERROR")
                .message(errorMessage)
                .build();
    }
}
