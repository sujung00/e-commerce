package com.hhplus.ecommerce.application.user;

import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.domain.user.InsufficientBalanceException;
import com.hhplus.ecommerce.domain.order.ChildTransactionEvent;
import com.hhplus.ecommerce.domain.order.ChildTransactionEventRepository;
import com.hhplus.ecommerce.domain.order.ChildTxType;
import com.hhplus.ecommerce.domain.order.EventStatus;
import com.hhplus.ecommerce.infrastructure.lock.DistributedLock;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

/**
 * UserBalanceService - 사용자 잔액 관리 서비스 (Application 계층)
 *
 * 역할:
 * - 사용자 잔액 충전/차감 비즈니스 로직 처리
 * - Redis 기반 분산락으로 동시성 제어
 * - DB 레벨 비관적 락으로 추가 안전성 보장
 *
 * 동시성 제어:
 * - @DistributedLock: Redis 분산락 (key: "balance:{userId}")
 *   - 분산 환경에서 여러 서버의 동시 접근 방지
 * - findByIdForUpdate(): DB 레벨 비관적 락 (SELECT ... FOR UPDATE)
 *   - 단일 DB 내에서 동시 트랜잭션의 Lost Update 방지
 * - @Transactional: 트랜잭션 경계 설정
 *   - 잔액 변경이 원자적으로 처리
 *
 * 아키텍처:
 * - OrderTransactionService, OrderCancelTransactionService에서 호출됨
 * - 잔액 차감/환불을 별도 서비스로 분리하여 프록시 기반 AOP 적용 가능
 */
@Service
public class UserBalanceService {

    private static final Logger log = LoggerFactory.getLogger(UserBalanceService.class);

    private final UserRepository userRepository;
    private final ChildTransactionEventRepository childTransactionEventRepository;
    private final ObjectMapper objectMapper;

    public UserBalanceService(
            UserRepository userRepository,
            ChildTransactionEventRepository childTransactionEventRepository,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.childTransactionEventRepository = childTransactionEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 사용자 잔액 차감 (결제) - Event 기록 버전
     *
     * ⚠️ 개선사항 (Outbox 패턴):
     * - Parent TX 실패 시 자동 보상 가능하도록 ChildTransactionEvent 저장
     * - Event는 Child TX와 동일한 트랜잭션에서 저장되므로 원자성 보장
     * - Parent TX 실패 후에도 Event는 이미 커밋되어 보상 가능
     *
     * 호출 방식:
     * - OrderTransactionService에서 orderId를 함께 전달
     * - 기존 코드와의 호환성 유지: orderId가 없으면 기존 deductBalance(userId, amount) 호출
     *
     * 중요한 예외 처리 전략:
     * 1. 이 메서드는 @Transactional(propagation = REQUIRES_NEW)로 독립적인 자식 트랜잭션
     *    - 부모 트랜잭션과 독립적으로 커밋/롤백
     *    - 예외가 발생해도 부모는 계속 실행 가능
     *    - 하지만 대부분의 예외는 부모로 전파되어야 함 (주문 실패)
     *
     * 2. @DistributedLock과 REQUIRES_NEW의 상호작용
     *    - Lock 획득 전에 TX가 생성됨
     *    - Lock 실패 시 RuntimeException → TX 롤백 (TX가 시작되지 않음)
     *    - Lock 성공 시 TX 커밋 또는 예외 발생
     *    - Lock 해제는 TransactionSynchronization 콜백으로 처리
     *
     * 3. 도메인 검증 예외
     *    - IllegalArgumentException: 금액이 0 이하
     *    - InsufficientBalanceException: 잔액 부족
     *    - 이들은 모두 RuntimeException을 상속받으므로 자동 롤백
     *
     * 4. Event 저장 실패 처리
     *    - Event 저장 실패는 try-catch로 처리하여 메인 로직에 영향 주지 않음
     *    - 로깅하지만 롤백하지 않음 (보상용 Event이므로)
     *
     * 동시성 제어 전략:
     * - @DistributedLock: Redis 기반 분산락 (key: "balance:{userId}")
     * - findByIdForUpdate(): DB 레벨 비관적 락 (SELECT ... FOR UPDATE)
     * - @Transactional(propagation = REQUIRES_NEW): 독립적 자식 트랜잭션
     *
     * @param userId 사용자 ID
     * @param amount 차감할 금액
     * @param orderId 주문 ID (Event 기록용, Outbox 패턴)
     * @return 차감 후 사용자 정보
     * @throws UserNotFoundException 사용자를 찾을 수 없음
     * @throws InsufficientBalanceException 잔액 부족
     * @throws IllegalArgumentException 금액이 0 이하
     * @throws RuntimeException 분산락 획득 실패
     */
    @DistributedLock(key = "balance:#p0", waitTime = 5, leaseTime = 2)
    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        rollbackFor = Exception.class
    )
    public User deductBalance(Long userId, Long amount, Long orderId) {
        return deductBalanceInternal(userId, amount, orderId);
    }

    /**
     * 사용자 잔액 차감 (결제) - 기존 호환성 유지 버전
     *
     * 기존 코드와의 호환성을 위해 유지
     * orderId가 필요한 경우 deductBalance(userId, amount, orderId)를 사용
     *
     * @param userId 사용자 ID
     * @param amount 차감할 금액
     * @return 차감 후 사용자 정보
     * @throws UserNotFoundException 사용자를 찾을 수 없음
     * @throws InsufficientBalanceException 잔액 부족
     * @throws IllegalArgumentException 금액이 0 이하
     * @throws RuntimeException 분산락 획득 실패
     */
    @DistributedLock(key = "balance:#p0", waitTime = 5, leaseTime = 2)
    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        rollbackFor = Exception.class
    )
    public User deductBalance(Long userId, Long amount) {
        return deductBalanceInternal(userId, amount, null);
    }

    /**
     * 사용자 잔액 차감 내부 구현
     *
     * @param userId 사용자 ID
     * @param amount 차감할 금액
     * @param orderId 주문 ID (null 가능, Event 기록용)
     * @return 차감 후 사용자 정보
     */
    private User deductBalanceInternal(Long userId, Long amount, Long orderId) {
        try {
            // 1. 사용자 조회 (비관적 락 획득)
            // SELECT users WHERE user_id=? FOR UPDATE
            User user = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));

            // 2-3. 잔액 검증 및 차감 (Domain 메서드)
            Long balanceBefore = user.getBalance();
            user.deductBalance(amount);
            Long balanceAfter = user.getBalance();

            // 4. 변경사항 저장
            userRepository.save(user);

            log.info("[UserBalanceService] 잔액 차감 완료: userId={}, amount={}, before={}, after={}, version={}",
                    userId, amount, balanceBefore, balanceAfter, user.getVersion());

            // ✅ Outbox 패턴: Event 저장 (보상용)
            // orderId가 있을 때만 Event 저장
            // Event는 Child TX와 동일한 트랜잭션에서 저장되므로 원자성 보장
            if (orderId != null) {
                try {
                    String eventData = objectMapper.writeValueAsString(
                            Map.of(
                                    "userId", userId,
                                    "deductedAmount", amount,
                                    "balanceBefore", balanceBefore,
                                    "balanceAfter", balanceAfter
                            )
                    );

                    ChildTransactionEvent event = ChildTransactionEvent.create(
                            orderId,
                            userId,
                            ChildTxType.BALANCE_DEDUCT,
                            eventData
                    );

                    childTransactionEventRepository.save(event);

                    log.info("[UserBalanceService] 잔액 차감 Event 저장 완료: orderId={}, userId={}, amount={}, eventId={}",
                            orderId, userId, amount, event.getEventId());

                } catch (Exception e) {
                    // Event 저장 실패 시 로깅하지만 메인 로직에 영향 주지 않음
                    // 이미 잔액은 차감되었으므로 롤백하지 않음
                    log.error("[UserBalanceService] Event 저장 실패 (무시됨): userId={}, orderId={}, error={}",
                            userId, orderId, e.getMessage(), e);
                    // 실무에서는 이 경우를 알림 발송 대상으로 표기할 수 있음
                }
            }

            return user;

        } catch (UserNotFoundException e) {
            log.error("[UserBalanceService] 사용자를 찾을 수 없음: userId={}, orderId={}, exception={}",
                    userId, orderId, e.getMessage());
            throw e;

        } catch (InsufficientBalanceException e) {
            log.error("[UserBalanceService] 잔액 부족: userId={}, orderId={}, required={}, exception={}",
                    userId, orderId, amount, e.getMessage());
            throw e;

        } catch (RuntimeException e) {
            log.error("[UserBalanceService] 런타임 예외: userId={}, orderId={}, amount={}, exception={}",
                    userId, orderId, amount, e.getMessage());
            throw e;
        }
    }

    /**
     * 사용자 잔액 충전
     *
     * 동시성 제어 전략:
     * - @DistributedLock: Redis 기반 분산락 (key: "balance:{userId}")
     *   - 여러 충전 요청의 동시 접근 방지
     * - findByIdForUpdate(): DB 레벨 비관적 락
     *   - 동시 차감/충전 시 Lost Update 방지
     * - @Transactional: 트랜잭션 경계 설정
     *
     * 비즈니스 로직:
     * 1. 사용자 조회 (비관적 락 획득)
     * 2. 잔액 충전 검증
     * 3. 잔액 증가
     * 4. 변경사항 저장
     *
     * @param userId 사용자 ID
     * @param amount 충전할 금액
     * @return 충전 후 사용자 정보
     * @throws UserNotFoundException 사용자를 찾을 수 없음
     * @throws IllegalArgumentException 금액이 0 이하
     * @throws RuntimeException 분산락 획득 실패
     */
    @DistributedLock(key = "balance:#p0", waitTime = 5, leaseTime = 2)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User chargeBalance(Long userId, Long amount) {
        // 1. 사용자 조회 (비관적 락 획득)
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 2-3. 잔액 충전 (Domain 메서드)
        // User.chargeBalance()가 다음을 처리합니다:
        // - 금액이 0 이하인 경우 IllegalArgumentException
        // - 유효한 금액인 경우 잔액 증가 및 updatedAt 갱신
        user.chargeBalance(amount);

        // 4. 변경사항 저장
        userRepository.save(user);

        log.info("[UserBalanceService] 잔액 충전 완료: userId={}, amount={}, newBalance={}, version={}",
                userId, amount, user.getBalance(), user.getVersion());

        return user;
    }

    /**
     * 사용자 잔액 환불 (주문 취소 시)
     *
     * 동시성 제어 전략:
     * - @DistributedLock: Redis 기반 분산락
     * - findByIdForUpdate(): DB 레벨 비관적 락
     * - @Transactional: 트랜잭션 경계 설정
     *
     * 비즈니스 로직:
     * 1. 사용자 조회 (비관적 락 획득)
     * 2. 잔액 환불 검증
     * 3. 잔액 증가
     * 4. 변경사항 저장
     *
     * @param userId 사용자 ID
     * @param amount 환불할 금액
     * @return 환불 후 사용자 정보
     * @throws UserNotFoundException 사용자를 찾을 수 없음
     * @throws IllegalArgumentException 금액이 0 이하
     * @throws RuntimeException 분산락 획득 실패
     */
    @DistributedLock(key = "balance:#p0", waitTime = 5, leaseTime = 2)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User refundBalance(Long userId, Long amount) {
        // 1. 사용자 조회 (비관적 락 획득)
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 2-3. 잔액 환불 (Domain 메서드)
        // User.refundBalance()가 다음을 처리합니다:
        // - 금액이 0 이하인 경우 IllegalArgumentException
        // - 유효한 금액인 경우 잔액 증가 및 updatedAt 갱신
        user.refundBalance(amount);

        // 4. 변경사항 저장
        userRepository.save(user);

        log.info("[UserBalanceService] 잔액 환불 완료: userId={}, amount={}, newBalance={}, version={}",
                userId, amount, user.getBalance(), user.getVersion());

        return user;
    }
}
