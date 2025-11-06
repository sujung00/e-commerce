package com.hhplus.ecommerce.domain.user;

import java.util.Optional;

/**
 * User Repository Interface (Domain Layer - Port)
 */
public interface UserRepository {
    Optional<User> findById(Long userId);

    boolean existsById(Long userId);

    void save(User user);
}