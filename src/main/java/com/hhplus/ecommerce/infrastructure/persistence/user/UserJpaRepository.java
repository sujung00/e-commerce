package com.hhplus.ecommerce.infrastructure.persistence.user;

import com.hhplus.ecommerce.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * User JPA Repository
 * Spring Data JPA를 통한 User 엔티티 영구 저장소
 */
public interface UserJpaRepository extends JpaRepository<User, Long> {
}
