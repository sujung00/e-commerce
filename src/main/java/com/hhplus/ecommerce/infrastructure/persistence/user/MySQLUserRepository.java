package com.hhplus.ecommerce.infrastructure.persistence.user;

import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * MySQL 기반 User Repository 구현
 * Spring Data JPA를 사용한 영구 저장소
 *
 * Port(UserRepository) 인터페이스를 구현하면서 JpaRepository 기능 제공
 *
 * Lock 전략:
 * - findById(): 낙관적 락 (조회만)
 * - findByIdForUpdate(): 비관적 락 (SELECT ... FOR UPDATE)
 */
@Repository
@Primary
public class MySQLUserRepository implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    public MySQLUserRepository(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public void save(User user) {
        userJpaRepository.save(user);
    }

    @Override
    public Optional<User> findById(Long userId) {
        return userJpaRepository.findById(userId);
    }

    /**
     * 사용자를 비관적 락으로 조회
     *
     * 동시성 제어:
     * - SELECT ... FOR UPDATE로 DB 레벨 exclusive lock 획득
     * - 동시 충전/결제 시 Lost Update 방지
     * - 잔액 변경 작업이 완료될 때까지 다른 트랜잭션은 대기
     *
     * @param userId 사용자 ID
     * @return 비관적 락으로 획득된 사용자 정보
     */
    @Override
    public Optional<User> findByIdForUpdate(Long userId) {
        return userJpaRepository.findByIdForUpdate(userId);
    }

    @Override
    public boolean existsById(Long userId) {
        return userJpaRepository.existsById(userId);
    }
}
