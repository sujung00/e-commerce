package com.hhplus.ecommerce.domain.cart;

import java.util.Optional;

import java.util.List;

/**
 * Cart Repository Interface (Domain Layer - Port)
 * 의존성 역전: 구현체는 이 인터페이스에 의존한다.
 */
public interface CartRepository {
    /**
     * 사용자의 장바구니 조회 또는 생성
     */
    Cart findOrCreateByUserId(Long userId);

    /**
     * 사용자의 장바구니 조회
     */
    Optional<Cart> findByUserId(Long userId);

    /**
     * 장바구니 아이템 ID로 조회
     */
    Optional<CartItem> findCartItemById(Long cartItemId);

    /**
     * 장바구니 아이템 저장 (생성 또는 수정)
     */
    CartItem saveCartItem(CartItem cartItem);

    /**
     * 장바구니 아이템 삭제
     */
    void deleteCartItem(Long cartItemId);

    /**
     * 장바구니 저장 (생성 또는 수정)
     */
    Cart saveCart(Cart cart);

    /**
     * 특정 장바구니의 모든 아이템 조회
     *
     * @param cartId 장바구니 ID
     * @return 해당 장바구니의 모든 아이템 리스트
     */
    List<CartItem> getCartItems(Long cartId);

    /**
     * 장바구니에서 특정 상품+옵션 조합으로 아이템 조회
     * 중복 항목 확인 및 수량 누적 처리용
     *
     * @param cartId 장바구니 ID
     * @param productId 상품 ID
     * @param optionId 옵션 ID
     * @return 해당 아이템 (있으면), 없으면 empty
     */
    Optional<CartItem> findCartItem(Long cartId, Long productId, Long optionId);
}