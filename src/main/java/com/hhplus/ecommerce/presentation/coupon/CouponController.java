package com.hhplus.ecommerce.presentation.coupon;

import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.application.coupon.CouponQueueService;
import com.hhplus.ecommerce.application.coupon.dto.CouponIssueStatusResponse;
import com.hhplus.ecommerce.presentation.coupon.request.IssueCouponRequest;
import com.hhplus.ecommerce.presentation.coupon.response.CouponIssueAsyncResponse;
import com.hhplus.ecommerce.presentation.coupon.response.GetAvailableCouponsResponse;
import com.hhplus.ecommerce.presentation.coupon.response.GetUserCouponsResponse;
import com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    private static final Logger log = LoggerFactory.getLogger(CouponController.class);

    private final CouponService couponService;
    private final CouponQueueService couponQueueService;

    public CouponController(
            CouponService couponService,
            CouponQueueService couponQueueService) {
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
     * 역할 (Presentation 계층):
     * - HTTP 요청 파라미터 추출
     * - 서비스 호출
     * - HTTP 상태 코드 설정
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

        CouponIssueAsyncResponse response = couponQueueService.issueCouponAsync(userId, request.getCouponId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * 4.1-kafka 쿠폰 발급 (Kafka 기반 비동기)
     * POST /api/coupons/issue/kafka
     *
     * 역할 (Presentation 계층):
     * - HTTP 요청 파라미터 추출
     * - 서비스 호출
     * - HTTP 상태 코드 설정
     *
     * 특징:
     * - Kafka 기반 비동기 처리
     * - 동기 대기 후 응답 (< 5초, 202 Accepted)
     * - couponId 기반 파티셔닝으로 선착순 보장 (✅ 개선: userId → couponId)
     * - 10개 파티션 + 10개 Consumer = 쿠폰별 순차 처리
     * - 순서 보장: 같은 쿠폰 요청은 순서대로 처리
     *
     * @param userId X-USER-ID 헤더 (사용자 ID)
     * @param request 쿠폰 발급 요청 (coupon_id)
     * @return 요청 ID (202 Accepted) 또는 에러 (500 Internal Server Error)
     */
    @PostMapping("/issue/kafka")
    public ResponseEntity<CouponIssueAsyncResponse> issueCouponKafka(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestBody IssueCouponRequest request) {

        CouponIssueAsyncResponse response = couponService.issueCouponKafka(userId, request.getCouponId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * 4.1-status 쿠폰 발급 상태 조회 (폴링용)
     * GET /api/coupons/issue/status/{requestId}
     *
     * 역할 (Presentation 계층):
     * - HTTP 요청 파라미터 추출
     * - 서비스 호출
     * - HTTP 상태 코드 설정
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

        CouponIssueStatusResponse response = couponQueueService.getRequestStatus(requestId);
        return ResponseEntity.ok(response);
    }

    /**
     * 4.2 사용자가 보유한 쿠폰 조회
     * GET /api/coupons/issued?status=ACTIVE
     *
     * 역할 (Presentation 계층):
     * - HTTP 요청 파라미터 추출
     * - 서비스 호출
     * - HTTP 상태 코드 설정
     *
     * @param userId X-USER-ID 헤더 (사용자 ID)
     * @param status 쿠폰 상태 (ACTIVE | USED | EXPIRED), 기본값: "ACTIVE"
     * @return 사용자 쿠폰 목록 (200 OK)
     */
    @GetMapping("/issued")
    public ResponseEntity<GetUserCouponsResponse> getUserCoupons(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestParam(required = false, defaultValue = "ACTIVE") String status) {
        GetUserCouponsResponse response = couponService.getUserCoupons(userId, status);
        return ResponseEntity.ok(response);
    }

    /**
     * 4.3 사용 가능한 쿠폰 조회
     * GET /api/coupons
     *
     * 역할 (Presentation 계층):
     * - HTTP 요청 파라미터 추출
     * - 서비스 호출
     * - HTTP 상태 코드 설정
     *
     * @return 발급 가능한 쿠폰 목록 (200 OK)
     */
    @GetMapping
    public ResponseEntity<GetAvailableCouponsResponse> getAvailableCoupons() {
        GetAvailableCouponsResponse response = couponService.getAvailableCoupons();
        return ResponseEntity.ok(response);
    }
}
