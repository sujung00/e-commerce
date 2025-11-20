package com.hhplus.ecommerce.infrastructure.persistence.product;

import com.hhplus.ecommerce.domain.product.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

/**
 * ProductOption JPA Repository
 * Spring Data JPA를 통한 ProductOption 엔티티 영구 저장소
 */
public interface ProductOptionJpaRepository extends JpaRepository<ProductOption, Long> {
    List<ProductOption> findByProductId(Long productId);

    /**
     * 비관적 락(Pessimistic Lock)을 사용하여 ProductOption 조회
     * SELECT ... FOR UPDATE 쿼리로 즉시 락 획득
     *
     * 용도: 재고 차감 시 동시성 제어
     * 장점:
     * - 즉시 락 획득으로 Race Condition 방지
     * - 다중 프로세스 환경에서도 안전
     * - DB 레벨에서 동시성 보장
     *
     * 단점:
     * - 처리량 감소 (대기 시간 발생)
     * - Lock timeout 가능성
     *
     * 예시:
     * Thread A가 lock 획득 → stock 검사 → 차감 → 해제
     * Thread B는 Thread A의 lock 해제까지 대기
     * 결과: 정확한 재고 관리
     *
     * @param optionId 옵션 ID
     * @return 비관적 락이 적용된 ProductOption
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT po FROM ProductOption po WHERE po.optionId = :optionId")
    Optional<ProductOption> findByIdForUpdate(Long optionId);
}
