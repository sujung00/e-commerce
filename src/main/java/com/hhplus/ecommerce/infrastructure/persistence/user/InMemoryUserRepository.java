package com.hhplus.ecommerce.infrastructure.persistence.user;

import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InMemory User Repository 구현
 */
@Repository
public class InMemoryUserRepository implements UserRepository {

    private final ConcurrentHashMap<Long, User> users = new ConcurrentHashMap<>();

    public InMemoryUserRepository() {
        initializeSampleData();
    }

    /**
     * 샘플 사용자 데이터 초기화
     */
    private void initializeSampleData() {
        User user1 = User.builder()
                .userId(100L)
                .email("user100@example.com")
                .name("사용자100")
                .phone("010-0000-0000")
                .balance(1000000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        users.put(100L, user1);

        User user2 = User.builder()
                .userId(101L)
                .email("user101@example.com")
                .name("사용자101")
                .phone("010-1111-1111")
                .balance(500000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        users.put(101L, user2);
    }

    @Override
    public Optional<User> findById(Long userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public boolean existsById(Long userId) {
        return users.containsKey(userId);
    }

    @Override
    public void save(User user) {

    }

}
