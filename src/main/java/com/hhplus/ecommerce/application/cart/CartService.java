package com.hhplus.ecommerce.application.cart;

import com.hhplus.ecommerce.domain.cart.*;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.presentation.cart.request.AddCartItemRequest;
import com.hhplus.ecommerce.presentation.cart.request.UpdateQuantityRequest;
import com.hhplus.ecommerce.presentation.cart.response.CartItemResponse;
import com.hhplus.ecommerce.presentation.cart.response.CartResponseDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CartService - Application 계층
 * 비즈니스 로직 처리
 *
 * 아키텍처:
 * - Domain 계층의 CartRepository 인터페이스에만 의존 (Port)
 * - Infrastructure 계층의 구현체는 DI를 통해 주입됨 (Adapter)
 * - Port-Adapter 패턴 준수로 느슨한 결합 유지
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final UserRepository userRepository;

    public CartService(CartRepository cartRepository,
                       UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
    }

    /**
     * 사용자의 장바구니 조회
     */
    public CartResponseDto getCartByUserId(Long userId) {
        // 사용자 존재 검증
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        Cart cart = cartRepository.findOrCreateByUserId(userId);
        List<CartItem> cartItems = cartRepository.getCartItems(cart.getCartId());

        // CartItem을 Response로 변환
        List<CartItemResponse> itemResponses = cartItems.stream()
                .map(item -> CartItemResponse.from(item, getProductName(item.getProductId()), getOptionName(item.getOptionId())))
                .collect(Collectors.toList());

        // 장바구니 정보 업데이트
        int totalItems = cartItems.size();
        long totalPrice = cartItems.stream().mapToLong(CartItem::getSubtotal).sum();

        return CartResponseDto.builder()
                .cartId(cart.getCartId())
                .userId(cart.getUserId())
                .totalItems(totalItems)
                .totalPrice(totalPrice)
                .items(itemResponses)
                .updatedAt(cart.getUpdatedAt())
                .build();
    }

    /**
     * 장바구니에 아이템 추가
     */
    public CartItemResponse addItem(Long userId, AddCartItemRequest request) {
        // 사용자 존재 검증
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        // 수량 검증
        validateQuantity(request.getQuantity());

        // 장바구니 조회 또는 생성
        Cart cart = cartRepository.findOrCreateByUserId(userId);

        // CartItem 생성
        CartItem cartItem = CartItem.builder()
                .cartId(cart.getCartId())
                .productId(request.getProductId())
                .optionId(request.getOptionId())
                .quantity(request.getQuantity())
                .unitPrice(getProductPrice(request.getProductId()))
                .subtotal((long) request.getQuantity() * getProductPrice(request.getProductId()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // CartItem 저장
        CartItem savedItem = cartRepository.saveCartItem(cartItem);

        // 장바구니 업데이트
        updateCartTotals(cart);

        return CartItemResponse.from(savedItem,
                getProductName(savedItem.getProductId()),
                getOptionName(savedItem.getOptionId()));
    }

    /**
     * 장바구니 아이템 수량 수정
     */
    public CartItemResponse updateItemQuantity(Long userId, Long cartItemId, UpdateQuantityRequest request) {
        // 사용자 존재 검증
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        // 수량 검증
        validateQuantity(request.getQuantity());

        // CartItem 조회
        CartItem cartItem = cartRepository.findCartItemById(cartItemId)
                .orElseThrow(() -> new CartItemNotFoundException(cartItemId));

        // 사용자의 아이템 확인
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!cartItem.getCartId().equals(cart.getCartId())) {
            throw new CartItemNotFoundException(cartItemId);
        }

        // 수량 및 소계 업데이트
        cartItem.setQuantity(request.getQuantity());
        cartItem.setSubtotal((long) request.getQuantity() * cartItem.getUnitPrice());
        cartItem.setUpdatedAt(LocalDateTime.now());

        CartItem savedItem = cartRepository.saveCartItem(cartItem);

        // 장바구니 업데이트
        updateCartTotals(cart);

        return CartItemResponse.from(savedItem,
                getProductName(savedItem.getProductId()),
                getOptionName(savedItem.getOptionId()));
    }

    /**
     * 장바구니에서 아이템 제거
     */
    public void removeItem(Long userId, Long cartItemId) {
        // 사용자 존재 검증
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        // CartItem 조회
        CartItem cartItem = cartRepository.findCartItemById(cartItemId)
                .orElseThrow(() -> new CartItemNotFoundException(cartItemId));

        // 사용자의 아이템 확인
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!cartItem.getCartId().equals(cart.getCartId())) {
            throw new CartItemNotFoundException(cartItemId);
        }

        // 아이템 삭제
        cartRepository.deleteCartItem(cartItemId);

        // 장바구니 업데이트
        updateCartTotals(cart);
    }

    /**
     * 장바구니 총액 업데이트
     */
    private void updateCartTotals(Cart cart) {
        List<CartItem> items = cartRepository.getCartItems(cart.getCartId());
        int totalItems = items.size();
        long totalPrice = items.stream().mapToLong(CartItem::getSubtotal).sum();

        cart.setTotalItems(totalItems);
        cart.setTotalPrice(totalPrice);
        cart.setUpdatedAt(LocalDateTime.now());

        cartRepository.saveCart(cart);
    }

    /**
     * 수량 유효성 검증
     */
    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > 1000) {
            throw new InvalidQuantityException(quantity);
        }
    }

    /**
     * 상품명 조회 (샘플 데이터)
     */
    private String getProductName(Long productId) {
        return switch (productId.intValue()) {
            case 1 -> "티셔츠";
            case 2 -> "청바지";
            case 5 -> "슬리퍼";
            default -> "상품" + productId;
        };
    }

    /**
     * 옵션명 조회 (샘플 데이터)
     */
    private String getOptionName(Long optionId) {
        return switch (optionId.intValue()) {
            case 101 -> "블랙/M";
            case 102 -> "블랙/L";
            case 103 -> "화이트/M";
            case 201 -> "청색/32";
            case 501 -> "검정/260mm";
            case 502 -> "흰색/270mm";
            default -> "옵션" + optionId;
        };
    }

    /**
     * 상품 가격 조회 (샘플 데이터)
     */
    private Long getProductPrice(Long productId) {
        return switch (productId.intValue()) {
            case 1 -> 29900L;
            case 2 -> 79900L;
            case 5 -> 19900L;
            default -> 0L;
        };
    }
}
