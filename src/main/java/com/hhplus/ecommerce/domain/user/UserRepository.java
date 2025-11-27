package com.hhplus.ecommerce.domain.user;

import java.util.Optional;

/**
 * User Repository Interface (Domain Layer - Port)
 *
 * Lock 전략:
 * - findById(): 낙관적 락 (조회만)
 * - findByIdForUpdate(): 비관적 락 (잔액 변경 시)
 */
public interface UserRepository {
    /**
     * 사용자를 낙관적 락으로 조회
     *
     * @param userId 사용자 ID
     * @return 사용자 정보
     */
    Optional<User> findById(Long userId);

    /**
     * 사용자를 비관적 락으로 조회 (Lock 적용)
     *
     * 동시성 제어:
     * - SELECT ... FOR UPDATE로 DB 레벨 exclusive lock 획득
     * - 동시 충전/결제 시 Lost Update 방지
     * - 잔액 변경 작업이 완료될 때까지 다른 트랜잭션은 대기
     *
     * 사용처:
     * - OrderTransactionService.deductUserBalance()
     * - UserService.chargeBalance()
     * - 모든 잔액 변경 작업
     *
     * @param userId 사용자 ID
     * @return 비관적 락으로 획득된 사용자 정보
     */
    Optional<User> findByIdForUpdate(Long userId);

    /**
     * 사용자 존재 여부 확인
     *
     * @param userId 사용자 ID
     * @return 존재 여부
     */
    boolean existsById(Long userId);

    /**
     * 사용자 저장
     *
     * @param user 저장할 사용자 정보
     */
    void save(User user);
}