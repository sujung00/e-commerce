package com.hhplus.ecommerce.application.cart;

import com.hhplus.ecommerce.application.cart.dto.AddCartItemCommand;
import com.hhplus.ecommerce.application.cart.dto.CartItemResponse;
import com.hhplus.ecommerce.application.cart.dto.CartResponseDto;
import com.hhplus.ecommerce.application.cart.dto.UpdateQuantityCommand;
import com.hhplus.ecommerce.domain.cart.*;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.domain.user.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     *
     * ✅ 성능 최적화 (Message 5):
     * - Redis 캐시: TTL 3분으로 조회 성능 개선
     * - 캐시 전략: key = "cart:{userId}"
     * - 캐시 무효화: 장바구니 수정 시 자동 제거
     *
     * @param userId 사용자 ID
     * @return 장바구니 정보 (상품 목록, 총액 포함)
     */
    @Cacheable(
            value = "cartCache",
            key = "'cart:' + #userId",
            unless = "#result == null"
    )
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
     *
     * ✅ 캐시 무효화 (Message 5):
     * - @CacheEvict: 장바구니 수정 시 캐시 제거
     *
     * ✅ 동시성 제어:
     * - @Transactional: 트랜잭션 내에서 원자적 처리
     * - findByUserIdForUpdate: Cart 행에 비관적 락(SELECT FOR UPDATE)으로
     *   동시 항목 추가를 직렬화 → 수량 누적 정확성 보장
     *
     * 로직:
     * 1. 사용자 존재 검증
     * 2. 수량 검증
     * 3. 장바구니 조회 또는 생성 (없으면 생성)
     * 4. Cart 행에 비관적 락 획득
     * 5. 중복 항목 확인 (락 내에서 일관성 있는 읽기)
     *    - 있으면: 수량 업데이트
     *    - 없으면: 새 항목 생성
     * 6. 장바구니 총액 업데이트
     */
    @Transactional
    @CacheEvict(value = "cartCache", key = "'cart:' + #userId")
    public CartItemResponse addItem(Long userId, AddCartItemCommand command) {
        // 사용자 존재 검증
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        // 수량 검증
        validateQuantity(command.getQuantity());

        // 장바구니 조회 또는 생성 (아직 없으면 생성)
        cartRepository.findOrCreateByUserId(userId);

        // Cart 행에 비관적 락 획득 - 동시 요청 직렬화
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Cart not found after creation for userId=" + userId));

        // 중복 항목 확인 (SELECT FOR UPDATE - CURRENT READ)
        // ⚠️ InnoDB REPEATABLE READ 스냅샷 격리 우회 필수:
        // 일반 SELECT는 트랜잭션 시작 시점의 스냅샷을 읽으므로, 이미 다른 트랜잭션이
        // 커밋한 cart_item을 보지 못해 중복 INSERT → UNIQUE 제약 위반이 발생한다.
        // SELECT FOR UPDATE(CURRENT READ)는 항상 최신 커밋 데이터를 읽는다.
        var existingItem = cartRepository.findCartItemForUpdate(
                cart.getCartId(),
                command.getProductId(),
                command.getOptionId()
        );

        CartItem savedItem;
        if (existingItem.isPresent()) {
            // 이미 있으면 수량을 업데이트
            CartItem item = existingItem.get();
            int newQuantity = item.getQuantity() + command.getQuantity();
            validateQuantity(newQuantity);  // 업데이트 후 수량 검증

            item.setQuantity(newQuantity);
            item.setSubtotal((long) newQuantity * item.getUnitPrice());
            item.setUpdatedAt(LocalDateTime.now());

            savedItem = cartRepository.saveCartItem(item);
        } else {
            // 중복되지 않는 경우 새로 생성
            CartItem cartItem = CartItem.builder()
                    .cartId(cart.getCartId())
                    .productId(command.getProductId())
                    .optionId(command.getOptionId())
                    .quantity(command.getQuantity())
                    .unitPrice(getProductPrice(command.getProductId()))
                    .subtotal((long) command.getQuantity() * getProductPrice(command.getProductId()))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            savedItem = cartRepository.saveCartItem(cartItem);
        }

        // 장바구니 업데이트
        updateCartTotals(cart);

        return CartItemResponse.from(savedItem,
                getProductName(savedItem.getProductId()),
                getOptionName(savedItem.getOptionId()));
    }

    /**
     * 장바구니 아이템 수량 수정
     *
     * ✅ 캐시 무효화 (Message 5)
     */

    @CacheEvict(value = "cartCache", key = "'cart:' + #userId")
    public CartItemResponse updateItemQuantity(Long userId, Long cartItemId, UpdateQuantityCommand command) {
        // 사용자 존재 검증
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        // 수량 검증
        validateQuantity(command.getQuantity());

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
        cartItem.setQuantity(command.getQuantity());
        cartItem.setSubtotal((long) command.getQuantity() * cartItem.getUnitPrice());
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
     *
     * ✅ 캐시 무효화 (Message 5)
     */
    @CacheEvict(value = "cartCache", key = "'cart:' + #userId")
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
     *
     * ✅ VULN-002 수정:
     * - getCartItemsWithLock() (SELECT FOR UPDATE, CURRENT READ) 사용
     * - InnoDB REPEATABLE READ에서 스냅샷 격리를 우회해 다른 스레드가 커밋한
     *   cart_item들도 포함하여 정확한 합계를 계산
     * - 전제: 호출자가 이미 carts 행 비관적 락을 보유 중 (findByUserIdForUpdate)
     *   → 항상 carts 락 → cart_items 락 순서이므로 deadlock 없음
     */
    private void updateCartTotals(Cart cart) {
        // CURRENT READ로 모든 커밋된 + 현재 트랜잭션의 cart_item을 읽음
        List<CartItem> items = cartRepository.getCartItemsWithLock(cart.getCartId());
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
