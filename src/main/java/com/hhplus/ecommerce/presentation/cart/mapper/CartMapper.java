package com.hhplus.ecommerce.presentation.cart.mapper;

import com.hhplus.ecommerce.presentation.cart.request.AddCartItemRequest;
import com.hhplus.ecommerce.presentation.cart.request.UpdateQuantityRequest;
import com.hhplus.ecommerce.application.cart.dto.AddCartItemCommand;
import com.hhplus.ecommerce.application.cart.dto.UpdateQuantityCommand;
import com.hhplus.ecommerce.application.cart.dto.CartResponseDto;
import com.hhplus.ecommerce.application.cart.dto.CartItemResponse;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * CartMapper - Presentation layer와 Application layer 간의 DTO 변환
 *
 * 책임:
 * - Presentation Request DTO → Application Command 변환
 * - Application Response DTO → Presentation Response DTO 변환
 *
 * 아키텍처 원칙:
 * - Application layer는 Presentation layer DTO에 독립적 (자체 DTO 사용)
 * - Presentation layer의 @JsonProperty 같은 직렬화 로직은 이곳에서만 처리
 * - 각 계층이 자신의 DTO를 소유하고 관리하여 계층 간 의존성 제거
 */
@Component
public class CartMapper {

    /**
     * AddCartItemRequest → AddCartItemCommand로 변환
     */
    public AddCartItemCommand toAddCartItemCommand(AddCartItemRequest request) {
        return AddCartItemCommand.builder()
                .productId(request.getProductId())
                .optionId(request.getOptionId())
                .quantity(request.getQuantity())
                .build();
    }

    /**
     * UpdateQuantityRequest → UpdateQuantityCommand로 변환
     */
    public UpdateQuantityCommand toUpdateQuantityCommand(UpdateQuantityRequest request) {
        return UpdateQuantityCommand.builder()
                .quantity(request.getQuantity())
                .build();
    }

    /**
     * Application CartResponseDto → Presentation CartResponseDto로 변환
     */
    public com.hhplus.ecommerce.presentation.cart.response.CartResponseDto toCartResponseDto(CartResponseDto response) {
        return com.hhplus.ecommerce.presentation.cart.response.CartResponseDto.builder()
                .cartId(response.getCartId())
                .userId(response.getUserId())
                .totalItems(response.getTotalItems())
                .totalPrice(response.getTotalPrice())
                .items(response.getItems().stream()
                        .map(this::toCartItemResponse)
                        .collect(Collectors.toList()))
                .updatedAt(response.getUpdatedAt())
                .build();
    }

    /**
     * Application CartItemResponse → Presentation CartItemResponse로 변환
     */
    public com.hhplus.ecommerce.presentation.cart.response.CartItemResponse toCartItemResponse(CartItemResponse response) {
        return com.hhplus.ecommerce.presentation.cart.response.CartItemResponse.builder()
                .cartItemId(response.getCartItemId())
                .productId(response.getProductId())
                .productName(response.getProductName())
                .optionId(response.getOptionId())
                .optionName(response.getOptionName())
                .quantity(response.getQuantity())
                .unitPrice(response.getUnitPrice())
                .subtotal(response.getSubtotal())
                .build();
    }
}