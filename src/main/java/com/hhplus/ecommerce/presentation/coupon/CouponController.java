package com.hhplus.ecommerce.presentation.coupon;

import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.application.coupon.CouponQueueService;
import com.hhplus.ecommerce.application.coupon.dto.CouponIssueStatusResponse;
import com.hhplus.ecommerce.presentation.coupon.request.IssueCouponRequest;
import com.hhplus.ecommerce.presentation.coupon.response.AvailableCouponResponse;
import com.hhplus.ecommerce.presentation.coupon.response.CouponIssueAsyncResponse;
import com.hhplus.ecommerce.presentation.coupon.response.GetAvailableCouponsResponse;
import com.hhplus.ecommerce.presentation.coupon.response.GetUserCouponsResponse;
import com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse;
import com.hhplus.ecommerce.presentation.coupon.response.UserCouponResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CouponController - Presentation 계층
 *
 * 역할:
 * - HTTP 요청 처리
 * - 요청 헤더에서 userId 추출
 * - 응답 DTO 생성
 *
 * API 명세:
 * - 4.1 POST /coupons/issue (쿠폰 발급 - 동기)
 * - 4.1-async POST /coupons/issue/async (쿠폰 발급 - 비동기)
 * - 4.1-status GET /coupons/issue/status/{requestId} (발급 상태 조회)
 * - 4.2 GET /coupons/issued (사용자 쿠폰 조회)
 * - 4.3 GET /coupons (발급 가능한 쿠폰 조회)
 */
@RestController
@RequestMapping("/coupons")
public class CouponController {

    private final CouponService couponService;
    private final CouponQueueService couponQueueService;

    public CouponController(CouponService couponService, CouponQueueService couponQueueService) {
        this.couponService = couponService;
        this.couponQueueService = couponQueueService;
    }

    /**
     * 4.1 쿠폰 발급 (선착순 - 동기)
     * POST /api/coupons/issue
     *
     * @param userId X-USER-ID 헤더 (사용자 ID)
     * @param request 쿠폰 발급 요청 (coupon_id)
     * @return 발급된 쿠폰 정보 (201 Created)
     */
    @PostMapping("/issue")
    public ResponseEntity<IssueCouponResponse> issueCoupon(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestBody IssueCouponRequest request) {
        IssueCouponResponse response = couponService.issueCoupon(userId, request.getCouponId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 4.1-async 쿠폰 발급 (비동기, FIFO 보장)
     * POST /api/coupons/issue/async
     *
     * 특징:
     * - 즉시 응답 (< 10ms, 202 Accepted)
     * - Redis 큐에 요청 저장 (FIFO)
     * - 백그라운드 워커가 순서대로 처리
     * - 클라이언트는 requestId로 상태 폴링
     *
     * @param userId X-USER-ID 헤더 (사용자 ID)
     * @param request 쿠폰 발급 요청 (coupon_id)
     * @return 요청 ID (202 Accepted)
     */
    @PostMapping("/issue/async")
    public ResponseEntity<CouponIssueAsyncResponse> issueCouponAsync(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestBody IssueCouponRequest request) {

        // 기본 입력 검증
        if (userId == null || userId <= 0) {
            return ResponseEntity.badRequest().build();
        }
        if (request.getCouponId() == null || request.getCouponId() <= 0) {
            return ResponseEntity.badRequest().build();
        }

        // 캐시에서 쿠폰 존재 여부 빠르게 확인
        if (couponService.getAvailableCouponFromCache(request.getCouponId()) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CouponIssueAsyncResponse.error("쿠폰을 찾을 수 없습니다"));
        }

        // Redis 큐에 요청 추가
        String requestId = couponQueueService.enqueueCouponRequest(userId, request.getCouponId());

        // 202 Accepted 응답 (즉시 반환)
        CouponIssueAsyncResponse response = CouponIssueAsyncResponse.builder()
                .requestId(requestId)
                .status("PENDING")
                .message("쿠폰 발급 요청이 접수되었습니다. requestId로 상태를 확인하세요.")
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * 4.1-status 쿠폰 발급 상태 조회 (폴링용)
     * GET /api/coupons/issue/status/{requestId}
     *
     * 상태:
     * - PENDING: 처리 대기 중
     * - COMPLETED: 발급 완료
     * - FAILED: 발급 실패
     * - RETRY: 시스템 오류로 재시도 중
     * - NOT_FOUND: 요청을 찾을 수 없음
     *
     * @param requestId 쿠폰 발급 요청의 고유 ID
     * @return 발급 상태 및 결과 (200 OK)
     */
    @GetMapping("/issue/status/{requestId}")
    public ResponseEntity<CouponIssueStatusResponse> getIssueStatus(
            @PathVariable String requestId) {

        if (requestId == null || requestId.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        CouponIssueStatusResponse response = couponQueueService.getRequestStatus(requestId);
        return ResponseEntity.ok(response);
    }

    /**
     * 4.2 사용자가 보유한 쿠폰 조회
     * GET /api/coupons/issued?status=ACTIVE
     *
     * @param userId X-USER-ID 헤더 (사용자 ID)
     * @param status 쿠폰 상태 (ACTIVE | USED | EXPIRED), 기본값: "ACTIVE"
     * @return 사용자 쿠폰 목록 (200 OK)
     */
    @GetMapping("/issued")
    public ResponseEntity<GetUserCouponsResponse> getUserCoupons(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestParam(required = false, defaultValue = "ACTIVE") String status) {
        List<UserCouponResponse> userCoupons = couponService.getUserCoupons(userId, status);
        GetUserCouponsResponse response = GetUserCouponsResponse.builder()
                .userCoupons(userCoupons)
                .build();
        return ResponseEntity.ok(response);
    }

    /**
     * 4.3 사용 가능한 쿠폰 조회
     * GET /api/coupons
     *
     * @return 발급 가능한 쿠폰 목록 (200 OK)
     */
    @GetMapping
    public ResponseEntity<GetAvailableCouponsResponse> getAvailableCoupons() {
        List<AvailableCouponResponse> coupons = couponService.getAvailableCoupons();
        GetAvailableCouponsResponse response = GetAvailableCouponsResponse.builder()
                .coupons(coupons)
                .build();
        return ResponseEntity.ok(response);
    }
}
