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

    @Override
    public boolean existsById(Long userId) {
        return userJpaRepository.existsById(userId);
    }
}
