package com.hhplus.ecommerce.infrastructure.persistence.cart;

import com.hhplus.ecommerce.domain.cart.CartItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * CartItem JPA Repository
 * Spring Data JPA를 통한 CartItem 엔티티 영구 저장소
 */
public interface CartItemJpaRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCartId(Long cartId);

    /**
     * 장바구니에서 특정 상품+옵션 조합으로 아이템 조회
     * 중복 항목 확인 및 수량 누적 처리용
     *
     * @param cartId    장바구니 ID
     * @param productId 상품 ID
     * @param optionId  옵션 ID
     * @return 해당 아이템 (있으면), 없으면 empty
     */
    Optional<CartItem> findByCartIdAndProductIdAndOptionId(Long cartId, Long productId, Long optionId);

    /**
     * 장바구니에서 특정 상품+옵션 조합으로 아이템 조회 (SELECT ... FOR UPDATE)
     *
     * InnoDB REPEATABLE READ에서 스냅샷 격리를 우회하기 위해 CURRENT READ를 사용한다.
     * 동시 addItem 호출 시 cart 행 락 이후 cart_item 행도 CURRENT READ로 확인해야
     * 다른 트랜잭션이 이미 삽입한 항목을 볼 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ci FROM CartItem ci WHERE ci.cartId = :cartId AND ci.productId = :productId AND ci.optionId = :optionId")
    Optional<CartItem> findByCartIdAndProductIdAndOptionIdForUpdate(
            @Param("cartId") Long cartId,
            @Param("productId") Long productId,
            @Param("optionId") Long optionId);

    /**
     * 특정 장바구니의 모든 아이템 조회 (SELECT ... FOR UPDATE)
     *
     * VULN-002 수정용:
     * InnoDB REPEATABLE READ 스냅샷은 트랜잭션 시작 시점(snapshot read)을 기준으로 하므로,
     * 다른 스레드가 cart lock을 획득 후 커밋한 item들이 보이지 않는다.
     * SELECT FOR UPDATE(CURRENT READ)를 사용하면 최신 커밋 데이터를 읽어
     * 정확한 합계를 계산할 수 있다.
     *
     * 호출 전제: 호출자가 이미 carts 행에 비관적 락(SELECT FOR UPDATE)을 보유 중이어야 한다.
     * (deadlock 방지: 항상 carts 락 → cart_items 락 순서 유지)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ci FROM CartItem ci WHERE ci.cartId = :cartId")
    List<CartItem> findByCartIdForUpdate(@Param("cartId") Long cartId);
}
