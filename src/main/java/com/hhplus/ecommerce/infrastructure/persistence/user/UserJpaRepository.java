package com.hhplus.ecommerce.infrastructure.persistence.user;

import com.hhplus.ecommerce.domain.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * User JPA Repository
 * Spring Data JPA를 통한 User 엔티티 영구 저장소
 *
 * Lock 전략:
 * - findByIdForUpdate(): 동시 충전/결제 시 Lost Update 방지 (비관적 락)
 * - 사용자 잔액은 금전 거래이므로 낙관적 락보다 비관적 락 사용
 */
public interface UserJpaRepository extends JpaRepository<User, Long> {
    /**
     * 사용자를 비관적 락으로 조회
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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);
}
