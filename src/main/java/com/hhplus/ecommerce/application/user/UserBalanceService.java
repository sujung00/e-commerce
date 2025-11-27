package com.hhplus.ecommerce.application.user;

import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.domain.user.InsufficientBalanceException;
import com.hhplus.ecommerce.infrastructure.lock.DistributedLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public UserBalanceService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 사용자 잔액 차감 (결제)
     *
     * 동시성 제어 전략:
     * - @DistributedLock: Redis 기반 분산락 (key: "balance:{userId}")
     *   - waitTime=5초: 최대 5초 동안 락 획득 시도
     *   - leaseTime=2초: 락 유지 시간 2초
     *   - 락 획득 실패 시 RuntimeException 발생
     * - findByIdForUpdate(): DB 레벨 비관적 락 (SELECT ... FOR UPDATE)
     *   - 트랜잭션 내에서 행 레벨 배타적 잠금 획득
     *   - 동시 충전/결제 시 Lost Update 방지
     * - @Transactional: 메서드 전체를 하나의 트랜잭션으로 처리
     *   - 잔액 변경이 원자적으로 DB에 반영
     *
     * 비즈니스 로직:
     * 1. 사용자 조회 (비관적 락 획득)
     * 2. 잔액 검증 (User.deductBalance()에서 수행)
     * 3. 잔액 차감
     * 4. 변경사항 저장
     *
     * 흐름:
     * 1. Redis 분산락 획득 시도 (5초 내)
     * 2. DB에서 사용자 조회 (SELECT ... FOR UPDATE)
     * 3. User.deductBalance() 호출 (도메인 검증 수행)
     * 4. userRepository.save() 호출 (변경사항 저장)
     * 5. 트랜잭션 커밋
     * 6. Redis 분산락 해제
     *
     * @param userId 사용자 ID
     * @param amount 차감할 금액
     * @return 차감 후 사용자 정보
     * @throws UserNotFoundException 사용자를 찾을 수 없음
     * @throws InsufficientBalanceException 잔액 부족
     * @throws RuntimeException 분산락 획득 실패 또는 금액이 0 이하
     */
    @DistributedLock(key = "balance:#p0", waitTime = 5, leaseTime = 2)
    @Transactional
    public User deductBalance(Long userId, Long amount) {
        // 1. 사용자 조회 (비관적 락 획득)
        // SELECT users WHERE user_id=? FOR UPDATE
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 2-3. 잔액 검증 및 차감 (Domain 메서드)
        // User.deductBalance()가 다음을 처리합니다:
        // - 금액이 0 이하인 경우 IllegalArgumentException
        // - 잔액 부족 시 InsufficientBalanceException
        // - 유효한 금액인 경우 잔액 차감 및 updatedAt 갱신
        user.deductBalance(amount);

        // 4. 변경사항 저장
        userRepository.save(user);

        log.info("[UserBalanceService] 잔액 차감 완료: userId={}, amount={}, remainingBalance={}, version={}",
                userId, amount, user.getBalance(), user.getVersion());

        return user;
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
    @Transactional
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
    @Transactional
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
