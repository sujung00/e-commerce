package com.hhplus.ecommerce.presentation.product.mapper;

import com.hhplus.ecommerce.application.product.dto.ProductListResponse;
import com.hhplus.ecommerce.application.product.dto.ProductDetailResponse;
import com.hhplus.ecommerce.application.product.dto.ProductResponse;
import com.hhplus.ecommerce.application.product.dto.ProductOptionResponse;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * ProductMapper - Presentation layer와 Application layer 간의 DTO 변환
 *
 * 책임:
 * - Application Response DTO → Presentation Response DTO 변환
 *
 * 아키텍처 원칙:
 * - Application layer는 Presentation layer DTO에 독립적 (자체 DTO 사용)
 * - Presentation layer의 @JsonProperty 같은 직렬화 로직은 이곳에서만 처리
 * - 각 계층이 자신의 DTO를 소유하고 관리하여 계층 간 의존성 제거
 */
@Component
public class ProductMapper {

    /**
     * Application ProductListResponse → Presentation ProductListResponse로 변환
     */
    public com.hhplus.ecommerce.presentation.product.response.ProductListResponse toProductListResponse(ProductListResponse response) {
        return com.hhplus.ecommerce.presentation.product.response.ProductListResponse.builder()
                .content(response.getContent().stream()
                        .map(this::toProductResponse)
                        .collect(Collectors.toList()))
                .totalElements(response.getTotalElements())
                .totalPages(response.getTotalPages())
                .currentPage(response.getCurrentPage())
                .size(response.getSize())
                .build();
    }

    /**
     * Application ProductDetailResponse → Presentation ProductDetailResponse로 변환
     */
    public com.hhplus.ecommerce.presentation.product.response.ProductDetailResponse toProductDetailResponse(ProductDetailResponse response) {
        return new com.hhplus.ecommerce.presentation.product.response.ProductDetailResponse(
                response.getProductId(),
                response.getProductName(),
                response.getDescription(),
                response.getPrice(),
                response.getTotalStock(),
                response.getStatus(),
                response.getOptions().stream()
                        .map(this::toProductOptionResponse)
                        .collect(Collectors.toList()),
                response.getCreatedAt()
        );
    }

    /**
     * Application ProductResponse → Presentation ProductResponse로 변환
     */
    private com.hhplus.ecommerce.presentation.product.response.ProductResponse toProductResponse(ProductResponse response) {
        return new com.hhplus.ecommerce.presentation.product.response.ProductResponse(
                response.getProductId(),
                response.getProductName(),
                response.getDescription(),
                response.getPrice(),
                response.getTotalStock(),
                response.getStatus(),
                response.getCreatedAt()
        );
    }

    /**
     * Application ProductOptionResponse → Presentation ProductOptionResponse로 변환
     */
    private com.hhplus.ecommerce.presentation.product.response.ProductOptionResponse toProductOptionResponse(ProductOptionResponse response) {
        return new com.hhplus.ecommerce.presentation.product.response.ProductOptionResponse(
                response.getOptionId(),
                response.getName(),
                response.getStock(),
                response.getVersion()
        );
    }
}