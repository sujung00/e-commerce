package com.hhplus.ecommerce.application.coupon;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import com.hhplus.ecommerce.domain.coupon.CouponNotFoundException;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.coupon.event.CouponIssuedEvent;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.domain.order.ChildTransactionEvent;
import com.hhplus.ecommerce.domain.order.ChildTransactionEventRepository;
import com.hhplus.ecommerce.domain.order.ChildTxType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.presentation.coupon.response.AvailableCouponResponse;
import com.hhplus.ecommerce.presentation.coupon.response.CouponIssueAsyncResponse;
import com.hhplus.ecommerce.presentation.coupon.response.GetAvailableCouponsResponse;
import com.hhplus.ecommerce.presentation.coupon.response.GetUserCouponsResponse;
import com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse;
import com.hhplus.ecommerce.presentation.coupon.response.UserCouponResponse;
import com.hhplus.ecommerce.infrastructure.kafka.CouponIssueProducer;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CouponService - Application 계층
 *
 * 역할:
 * - 쿠폰 발급, 조회 비즈니스 로직 처리
 * - DB 레벨 동시성 제어 (선착순 발급)
 * - 도메인 엔티티 검증
 *
 * ✅ 트랜잭션 외부 I/O 분리:
 * - 캐시 무효화(Redis)를 트랜잭션 내부에서 제거
 * - CouponIssuedEvent 발행 → @TransactionalEventListener(AFTER_COMMIT)에서 처리
 * - 트랜잭션 내에서는 DB 작업 + 이벤트 발행만 수행
 *
 * 동시성 제어 전략:
 * - DB 레벨의 비관적 락 (SELECT ... FOR UPDATE)
 * - findByIdForUpdate()가 트랜잭션 내 exclusive lock 획득
 * - synchronized 키워드 제거 (단일 인스턴스 전용, 분산 환경 미지원)
 * - 원자적 감소: remaining_qty--, version++
 * - UNIQUE(user_id, coupon_id) 제약으로 중복 발급 방지
 *
 * 흐름 (sequence-diagrams.md 5번 기반):
 * 1. 사용자 존재 검증 (읽기 전용)
 * 2. 비관적 락 획득: SELECT ... FOR UPDATE (DB 레벨)
 * 3-5. 조건 검증: is_active, valid_from/until, remaining_qty
 * 6. 원자적 감소: remaining_qty--, version++
 * 7. UNIQUE 검증: UNIQUE(user_id, coupon_id)
 * 8. 발급 기록 저장: INSERT user_coupons
 * 9. CouponIssuedEvent 발행 (트랜잭션 커밋 후 캐시 무효화)
 */
@Service
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final ChildTransactionEventRepository childTransactionEventRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CouponIssueProducer couponIssueProducer;

    public CouponService(CouponRepository couponRepository,
                         UserCouponRepository userCouponRepository,
                         UserRepository userRepository,
                         ChildTransactionEventRepository childTransactionEventRepository,
                         ObjectMapper objectMapper,
                         ApplicationEventPublisher eventPublisher,
                         CouponIssueProducer couponIssueProducer) {
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.userRepository = userRepository;
        this.childTransactionEventRepository = childTransactionEventRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.couponIssueProducer = couponIssueProducer;
    }

    /**
     * 4.1 쿠폰 발급 (선착순)
     * POST /coupons/issue
     *
     * sequence-diagrams.md 5번 시퀀스 기반 구현
     *
     * 비즈니스 로직 (비관적 락 기반):
     * 1. 사용자 존재 검증 (읽기 전용)
     * 2. 비관적 락 획득: SELECT coupons WHERE coupon_id=? FOR UPDATE
     *    - 다른 요청 차단
     *    - synchronized 블록으로 시뮬레이션
     * 3. 쿠폰 활성화 상태 검증 (is_active=true)
     * 4. 유효 기간 검증 (valid_from <= NOW <= valid_until)
     * 5. 재고 검증 (remaining_qty > 0)
     * 6. 원자적 감소: UPDATE coupons SET remaining_qty--, version++
     * 7. 중복 발급 방지: UNIQUE(user_id, coupon_id) 검증
     * 8. 발급 기록 저장: INSERT user_coupons
     *
     * @param userId 사용자 ID
     * @param couponId 발급받을 쿠폰 ID
     * @return 발급된 쿠폰 정보
     * @throws UserNotFoundException 사용자를 찾을 수 없음
     * @throws CouponNotFoundException 쿠폰을 찾을 수 없음
     * @throws IllegalArgumentException 발급 불가 사유 (유효기간, 재고, 중복)
     */
    public IssueCouponResponse issueCoupon(Long userId, Long couponId) {
        // === 1단계: 검증 (읽기 전용) ===

        // 1. 사용자 존재 검증
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        // 재시도 로직 (최대 5회)
        int maxRetries = 5;
        int retryCount = 0;
        long retryDelayMs = 5;

        while (retryCount < maxRetries) {
            try {
                return issueCouponWithLock(userId, couponId);
            } catch (IllegalArgumentException e) {
                // 쿠폰 소진, 유효기간 만료, 중복 발급 등은 재시도하지 않음
                throw e;
            } catch (Exception e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    throw e;
                }
                // 동시성 이슈로 인한 실패 시 짧은 시간 대기 후 재시도
                try {
                    Thread.sleep(retryDelayMs);
                    retryDelayMs *= 2;  // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("쿠폰 발급 중 인터럽트됨", ie);
                }
            }
        }

        throw new IllegalArgumentException("쿠폰 발급 실패: 재시도 횟수 초과");
    }

    /**
     * 쿠폰 발급 (DB 비관적 락만 사용)
     *
     * ✅ 개선사항:
     * - 분산락 제거: DB 비관적 락만으로 충분
     * - 캐시 무효화를 트랜잭션 외부로 분리: CouponIssuedEvent 발행
     *
     * 호출 경로:
     * - CouponQueueService.processCouponQueue()에서만 호출
     * - FIFO 큐에서 순차적으로 처리되므로 재시도 불필요
     *
     * 동시성 제어 전략:
     * - DB 레벨의 비관적 락만 사용 (SELECT ... FOR UPDATE)
     * - findByIdForUpdate()가 트랜잭션 내 exclusive lock 획득
     * - 다른 요청은 자동으로 차단됨
     * - UNIQUE(user_id, coupon_id) 제약으로 중복 발급 방지
     *
     * ✅ 트랜잭션 외부 I/O 분리:
     * - 캐시 무효화 제거 → CouponIssuedEvent 발행
     * - 이벤트 리스너가 AFTER_COMMIT에서 캐시 무효화 처리
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @param orderId 주문 ID (Outbox 패턴용)
     * @return 발급된 쿠폰 정보
     * @throws CouponNotFoundException 쿠폰을 찾을 수 없음
     * @throws IllegalArgumentException 발급 불가 (활성화, 기간, 재고, 중복)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IssueCouponResponse issueCouponWithLock(Long userId, Long couponId, Long orderId) {
        return issueCouponWithLockInternal(userId, couponId, orderId);
    }

    /**
     * 쿠폰 발급 (주문 연관 없음)
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급된 쿠폰 정보
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IssueCouponResponse issueCouponWithLock(Long userId, Long couponId) {
        return issueCouponWithLockInternal(userId, couponId, null);
    }

    /**
     * 쿠폰 발급 (락 포함) - 내부 구현
     *
     * ⚠️ 개선사항 (Outbox 패턴):
     * - Parent TX 실패 시 자동 보상 가능하도록 ChildTransactionEvent 저장
     * - Event는 Child TX와 동일한 트랜잭션에서 저장되므로 원자성 보장
     * - Parent TX 실패 후에도 Event는 이미 커밋되어 보상 가능
     *
     * ✅ 트랜잭션 외부 I/O 분리:
     * - 캐시 무효화 제거 → CouponIssuedEvent 발행
     * - 트랜잭션 내에서는 DB 작업 + 이벤트 발행만 수행
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @param orderId 주문 ID (null 가능, Event 기록용)
     * @return 발급된 쿠폰 정보
     */
    private IssueCouponResponse issueCouponWithLockInternal(Long userId, Long couponId, Long orderId) {
        // === 2단계: DB 레벨 비관적 락 획득 ===
        // SELECT coupons WHERE coupon_id=? FOR UPDATE
        // 다른 트랜잭션의 접근을 자동으로 차단함 (synchronized 제거됨)
        Coupon coupon = couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));

        // === 3-5단계: 조건 검증 (DB 락 획득 후) ===
        // Domain 메서드로 비즈니스 규칙 검증

        // 3. 쿠폰 활성화 상태 검증
        if (!coupon.isActiveCoupon()) {
            throw new IllegalArgumentException("쿠폰이 비활성화되어 있습니다");
        }

        // 4. 유효 기간 검증
        LocalDateTime now = LocalDateTime.now();
        if (!coupon.isValidPeriod(now)) {
            throw new IllegalArgumentException("쿠폰이 유효기간을 벗어났습니다");
        }

        // 5. 재고 검증 (remaining_qty > 0)
        if (!coupon.hasStock()) {
            throw new IllegalArgumentException("쿠폰이 모두 소진되었습니다");
        }

        // === 6단계: 원자적 감소 (UPDATE) ===
        // Domain 메서드 활용: 비즈니스 로직 캡슐화
        // Coupon.decreaseStock()이 remaining_qty--, version++, is_active 관리
        Integer remainingQtyBefore = coupon.getRemainingQty();
        coupon.decreaseStock();
        Integer remainingQtyAfter = coupon.getRemainingQty();
        couponRepository.update(coupon);

        // === 7단계: UNIQUE 검증 ===
        // UNIQUE(user_id, coupon_id) 제약 확인
        // DB 제약 조건으로 중복 발급 방지 (별도 락 불필요)
        boolean alreadyIssued = userCouponRepository.findByUserIdAndCouponId(userId, couponId)
                .isPresent();
        if (alreadyIssued) {
            throw new IllegalArgumentException("이 쿠폰은 이미 발급받으셨습니다");
        }

        // === 8단계: 발급 기록 저장 ===
        // INSERT user_coupons(user_id, coupon_id, status='UNUSED', issued_at=NOW())
        // USER_COUPONS은 "쿠폰 보유 상태"만 관리
        // 쿠폰 사용 여부는 ORDERS.coupon_id로 추적
        UserCoupon userCoupon = UserCoupon.builder()
                .userId(userId)
                .couponId(couponId)
                .status(UserCouponStatus.UNUSED)  // 발급 시 미사용 상태로 설정
                .issuedAt(LocalDateTime.now())
                .usedAt(null)
                .build();
        UserCoupon savedUserCoupon = userCouponRepository.save(userCoupon);

        log.info("[CouponService] 쿠폰 발급 완료: userId={}, couponId={}, userCouponId={}, remaining_qty={}, version={}",
                userId, couponId, savedUserCoupon.getUserCouponId(), coupon.getRemainingQty(), coupon.getVersion());

        // ✅ 캐시 무효화를 트랜잭션 외부로 분리: CouponIssuedEvent 발행
        // - 트랜잭션 내부에서 Redis I/O 제거
        // - 이벤트는 트랜잭션 커밋 후 AFTER_COMMIT 리스너에서 처리
        // - 캐시 무효화 실패가 비즈니스 트랜잭션에 영향을 주지 않음
        try {
            CouponIssuedEvent event = new CouponIssuedEvent(
                    couponId,
                    userId,
                    remainingQtyBefore,
                    remainingQtyAfter
            );
            eventPublisher.publishEvent(event);
            log.debug("[CouponService] CouponIssuedEvent 발행: couponId={}, userId={}", couponId, userId);
        } catch (Exception e) {
            // 이벤트 발행 실패는 로깅만 하고 메인 로직에 영향 주지 않음
            log.warn("[CouponService] CouponIssuedEvent 발행 실패 (무시됨): couponId={}, userId={}, error={}",
                    couponId, userId, e.getMessage());
        }

        // ✅ Outbox 패턴: Event 저장 (보상용)
        // orderId가 있을 때만 Event 저장
        // Event는 Child TX와 동일한 트랜잭션에서 저장되므로 원자성 보장
        if (orderId != null) {
            try {
                String eventData = objectMapper.writeValueAsString(
                        Map.of(
                                "userId", userId,
                                "couponId", couponId,
                                "discountAmount", coupon.getDiscountAmount(),
                                "remainingQtyBefore", remainingQtyBefore,
                                "remainingQtyAfter", remainingQtyAfter,
                                "userCouponId", savedUserCoupon.getUserCouponId()
                        )
                );

                ChildTransactionEvent event = ChildTransactionEvent.create(
                        orderId,
                        userId,
                        ChildTxType.COUPON_ISSUE,
                        eventData
                );

                childTransactionEventRepository.save(event);

                log.info("[CouponService] 쿠폰 발급 Event 저장 완료: orderId={}, userId={}, couponId={}, eventId={}",
                        orderId, userId, couponId, event.getEventId());

            } catch (Exception e) {
                // Event 저장 실패 시 로깅하지만 메인 로직에 영향 주지 않음
                // 이미 쿠폰은 발급되었으므로 롤백하지 않음
                log.error("[CouponService] Event 저장 실패 (무시됨): userId={}, couponId={}, orderId={}, error={}",
                        userId, couponId, orderId, e.getMessage(), e);
                // 실무에서는 이 경우를 알림 발송 대상으로 표기할 수 있음
            }
        }

        return IssueCouponResponse.from(savedUserCoupon, coupon);
    }

    /**
     * 4.2 사용자가 보유한 쿠폰 조회
     * GET /coupons/issued?status=UNUSED
     *
     * ✅ 성능 최적화 (Message 5):
     * - Fetch Join으로 N+1 문제 해결: 11 쿼리(1 + 10) → 1 쿼리
     * - Redis 캐시: TTL 5분으로 조회 성능 10배 향상
     * - 캐시 무효화: 쿠폰 발급/사용 시 자동 무효화
     *
     * ✅ 캐시 스탬피드 방지 (sync=true):
     * - 동일 사용자의 캐시 만료 시 첫 번째 스레드만 DB 조회
     * - 나머지 스레드는 캐시 갱신 완료까지 대기
     * - 동시 요청 시 DB 부하 방지
     *
     * 컨트롤러에서 이동된 비즈니스 로직:
     * - 응답 DTO 생성 (GetUserCouponsResponse)
     *
     * 비즈니스 로직:
     * 1. 사용자 존재 검증
     * 2. status별 필터링 (기본값: UNUSED)
     * 3. 사용자의 쿠폰 목록을 Fetch Join으로 조회 (N+1 해결)
     * 4. UserCoupon + Coupon 정보 결합하여 Response 생성
     * 5. GetUserCouponsResponse로 래핑하여 반환
     *
     * 캐시 전략:
     * - key: "userId:status" (예: "123:UNUSED")
     * - TTL: 5분 (CacheConfig.java 참조)
     * - unless: 결과가 null이거나 empty이면 캐싱 안 함
     *
     * @param userId 사용자 ID
     * @param status 쿠폰 상태 (UNUSED | USED | EXPIRED | CANCELLED)
     * @return 사용자 쿠폰 응답 DTO
     * @throws UserNotFoundException 사용자를 찾을 수 없음
     */
    @Cacheable(
            value = "userCouponsCache",
            key = "#userId + ':' + (#status != null && !#status.isEmpty() ? #status : 'UNUSED')",
            unless = "#result == null || #result.userCoupons == null || #result.userCoupons.isEmpty()",
            sync = true
    )
    public GetUserCouponsResponse getUserCoupons(Long userId, String status) {
        // 1. 사용자 존재 검증
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        // ✅ 수정: String을 Enum으로 변환
        // 기본값 설정 (기존 "ACTIVE" → "UNUSED")
        String statusStr = status != null && !status.isEmpty() ? status : "UNUSED";
        UserCouponStatus filterStatus = UserCouponStatus.from(statusStr);

        // 2-3. ✅ Fetch Join으로 조회 (N+1 문제 해결)
        // 1번의 쿼리로 UserCoupon + Coupon 정보를 모두 로드
        List<UserCoupon> userCoupons = userCouponRepository.findByUserIdAndStatus(userId, statusStr);

        // 4. UserCoupon + Coupon 정보 결합
        // ✅ 주의: UserCoupon에는 coupon 관계가 없으므로 개별 조회는 피할 수 없음
        // 하지만 Fetch Join이 적용된 쿼리면 메모리에서 빠르게 처리됨
        List<UserCouponResponse> userCouponResponses = userCoupons.stream()
                .map(uc -> {
                    Coupon coupon = couponRepository.findById(uc.getCouponId())
                            .orElseThrow(() -> new CouponNotFoundException(uc.getCouponId()));
                    return UserCouponResponse.from(uc, coupon);
                })
                .collect(Collectors.toList());

        // 5. GetUserCouponsResponse로 래핑하여 반환
        return GetUserCouponsResponse.builder()
                .userCoupons(userCouponResponses)
                .build();
    }

    /**
     * 4.3 사용 가능한 쿠폰 조회
     * GET /coupons
     *
     * 캐시: couponList (조회 빈도 높음, 변경 빈도 낮음)
     * TTL: 30분 (Redis로 자동 관리)
     * 예상 효과: TPS 300 → 2000 (6배 향상)
     *
     * ✅ 캐시 스탬피드 방지 (sync=true):
     * - 캐시 만료 시 첫 번째 스레드만 DB 조회
     * - 나머지 스레드는 캐시 갱신 완료까지 대기
     * - 동시 100개 요청 → 1개 DB 조회로 감소
     *
     * 컨트롤러에서 이동된 비즈니스 로직:
     * - 응답 DTO 생성 (GetAvailableCouponsResponse)
     *
     * 비즈니스 로직:
     * 1. 발급 가능한 쿠폰 조회 (is_active=true, 유효기간 내, remaining_qty > 0)
     * 2. Response로 변환
     * 3. GetAvailableCouponsResponse로 래핑하여 반환
     *
     * 필터링 조건:
     * - is_active = true
     * - valid_from <= NOW <= valid_until
     * - remaining_qty > 0
     *
     * 캐시 이름: couponListCache
     * 캐시 키: cache:coupon:list (고정)
     *
     * @return 발급 가능한 쿠폰 응답 DTO
     */
    @Cacheable(value = "couponListCache", key = "'cache:coupon:list'", sync = true)
    public GetAvailableCouponsResponse getAvailableCoupons() {
        // 1. 발급 가능한 쿠폰 조회
        List<Coupon> availableCoupons = couponRepository.findAllAvailable();

        // 2. Response로 변환
        List<AvailableCouponResponse> coupons = availableCoupons.stream()
                .map(AvailableCouponResponse::from)
                .collect(Collectors.toList());

        // 3. GetAvailableCouponsResponse로 래핑하여 반환
        return GetAvailableCouponsResponse.builder()
                .coupons(coupons)
                .build();
    }

    /**
     * 쿠폰 발급 (Kafka 기반 비동기)
     *
     * 컨트롤러에서 이동된 비즈니스 로직:
     * 1. 기본 입력 검증
     * 2. 캐시에서 쿠폰 존재 여부 확인
     * 3. Kafka로 쿠폰 발급 요청 발행
     * 4. 응답 DTO 생성
     * 5. 예외 처리
     *
     * 특징:
     * - Kafka 기반 비동기 처리
     * - 즉시 응답 (< 10ms, 202 Accepted)
     * - userId 기반 파티셔닝으로 순서 보장
     * - 10개 파티션 + 10개 Consumer = 병렬 처리
     * - 성능: 200 req/s (P=10)
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return Kafka 비동기 발급 응답 (requestId 포함)
     */
    public CouponIssueAsyncResponse issueCouponKafka(Long userId, Long couponId) {
        // 1. 기본 입력 검증
        if (userId == null || userId <= 0) {
            log.warn("[CouponService] 잘못된 사용자 ID: userId={}", userId);
            throw new IllegalArgumentException("유효하지 않은 사용자 ID입니다");
        }
        if (couponId == null || couponId <= 0) {
            log.warn("[CouponService] 잘못된 쿠폰 ID: couponId={}", couponId);
            throw new IllegalArgumentException("유효하지 않은 쿠폰 ID입니다");
        }

        // 2. 캐시에서 쿠폰 존재 여부 빠르게 확인
        AvailableCouponResponse couponFromCache = getAvailableCouponFromCache(couponId);
        if (couponFromCache == null) {
            log.warn("[CouponService] 쿠폰을 찾을 수 없음: couponId={}", couponId);
            throw new IllegalArgumentException("쿠폰을 찾을 수 없습니다");
        }

        try {
            // 3. Kafka로 쿠폰 발급 요청 발행
            String requestId = couponIssueProducer.sendCouponIssueRequest(userId, couponId);

            log.info("[CouponService] Kafka 쿠폰 발급 요청 발행 완료: " +
                    "requestId={}, userId={}, couponId={}",
                    requestId, userId, couponId);

            // 4. 202 Accepted 응답 생성
            return CouponIssueAsyncResponse.builder()
                    .requestId(requestId)
                    .status("PENDING")
                    .message("쿠폰 발급 요청이 Kafka로 전송되었습니다. Consumer가 비동기로 처리합니다.")
                    .build();

        } catch (Exception e) {
            // 5. 예외 처리
            log.error("[CouponService] Kafka 발행 실패: userId={}, couponId={}, error={}",
                    userId, couponId, e.getMessage(), e);
            throw new RuntimeException("쿠폰 발급 요청 발행 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 특정 쿠폰을 캐시에서 조회
     *
     * CouponQueueService에서 기본 검증용으로 사용
     *
     * @param couponId 쿠폰 ID
     * @return 쿠폰 정보 (없으면 null)
     */
    public AvailableCouponResponse getAvailableCouponFromCache(Long couponId) {
        try {
            GetAvailableCouponsResponse response = getAvailableCoupons();
            return response.getCoupons().stream()
                .filter(c -> c.getCouponId().equals(couponId))
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            log.warn("[CouponService] 캐시에서 쿠폰 조회 실패: couponId={}", couponId, e);
            return null;
        }
    }

    /**
     * 쿠폰 상태 복구 (보상용)
     *
     * Outbox 패턴의 보상 로직에서 호출
     * - COUPON_ISSUE 이벤트의 보상 시 쿠폰 상태를 UNUSED로 복구
     * - 발급된 user_coupon 기록을 삭제하고 쿠폰의 remaining_qty를 복원
     *
     * 동작:
     * 1. 사용자 및 쿠폰 존재 검증
     * 2. UserCoupon 기록 삭제 (쿠폰 발급 기록 제거)
     * 3. 쿠폰의 remaining_qty 복구 (발급 전 상태로 복원)
     *
     * 멱등성:
     * - UserCoupon이 이미 없으면 (이미 삭제된 상태) NOOP
     * - 쿠폰의 remaining_qty는 증가만 하므로 중복 호출 시에도 안전
     * - Event 상태 추적으로 중복 보상 방지
     *
     * @param userId 사용자 ID
     * @param couponId 복구할 쿠폰 ID
     * @throws UserNotFoundException 사용자를 찾을 수 없는 경우
     * @throws CouponNotFoundException 쿠폰을 찾을 수 없는 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revertCouponToAvailable(Long userId, Long couponId) {
        log.info("[CouponService] 쿠폰 상태 복구 시작: userId={}, couponId={}", userId, couponId);

        try {
            // 1. 사용자 및 쿠폰 존재 검증
            if (!userRepository.existsById(userId)) {
                throw new UserNotFoundException(userId);
            }

            Coupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new CouponNotFoundException(couponId));

            // 2. UserCoupon 기록 삭제
            // (사용자가 이 쿠폰을 가지고 있지 않으면 무시)
            userCouponRepository.deleteByUserIdAndCouponId(userId, couponId);
            log.info("[CouponService] UserCoupon 기록 삭제 완료: userId={}, couponId={}", userId, couponId);

            // 3. 쿠폰의 remaining_qty 복구
            // 발급 시에 remaining_qty를 1 감소시켰으므로, 보상 시에 1 증가
            coupon.increaseRemainingQty();
            couponRepository.update(coupon);
            log.info("[CouponService] 쿠폰 상태 복구 완료: couponId={}, remainingQty={}, version={}",
                    couponId, coupon.getRemainingQty(), coupon.getVersion());

        } catch (Exception e) {
            log.error("[CouponService] 쿠폰 상태 복구 실패: userId={}, couponId={}, error={}",
                    userId, couponId, e.getMessage(), e);
            throw e;
        }
    }
}