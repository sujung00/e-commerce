package com.hhplus.ecommerce.application;

import com.hhplus.ecommerce.domain.Product;
import com.hhplus.ecommerce.domain.ProductOption;
import com.hhplus.ecommerce.dto.*;
import com.hhplus.ecommerce.common.exception.ProductNotFoundException;
import com.hhplus.ecommerce.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ProductService - 상품 조회 비즈니스 로직 (Application 계층)
 * API 요청을 처리하고 Domain과 Infrastructure 계층 사이의 데이터 조합
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * 상품 목록 조회 with 페이지네이션 및 정렬
     *
     * @param page 페이지 번호 (0-based)
     * @param size 페이지당 항목 수
     * @param sort 정렬 기준 (필드명,방향)
     * @return 페이지네이션된 상품 목록
     */
    public ProductListResponse getProductList(int page, int size, String sort) {
        // 파라미터 검증
        if (page < 0) {
            throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("페이지 크기는 1 이상 100 이하여야 합니다");
        }

        // 모든 상품 조회
        List<Product> allProducts = productRepository.findAll();

        // 정렬 적용
        List<Product> sortedProducts = applySorting(allProducts, sort);

        // 페이지네이션 계산
        int totalElements = sortedProducts.size();
        int totalPages = (totalElements + size - 1) / size;
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, totalElements);

        // 페이지에 해당하는 상품만 추출 (startIndex가 범위를 벗어나면 빈 목록 반환)
        List<ProductResponse> pageContent = new ArrayList<>();
        if (startIndex < totalElements) {
            pageContent = sortedProducts.subList(startIndex, endIndex)
                    .stream()
                    .map(this::convertToProductResponse)
                    .collect(Collectors.toList());
        }

        return new ProductListResponse(pageContent, (long) totalElements,
                (long) totalPages, page, size);
    }

    /**
     * 상품 상세 조회 (옵션 포함)
     *
     * @param productId 상품 ID
     * @return 상품 상세 정보
     */
    public ProductDetailResponse getProductDetail(Long productId) {
        // 파라미터 검증
        if (productId <= 0) {
            throw new IllegalArgumentException("product_id는 양수여야 합니다");
        }

        // 상품 조회
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("상품을 찾을 수 없습니다 (ID: " + productId + ")"));

        // 옵션 조회
        List<ProductOption> options = productRepository.findOptionsByProductId(productId);

        // 옵션 응답 변환
        List<ProductOptionResponse> optionResponses = options.stream()
                .map(this::convertToProductOptionResponse)
                .collect(Collectors.toList());

        return new ProductDetailResponse(
                product.getProductId(),
                product.getProductName(),
                product.getDescription(),
                product.getPrice(),
                product.getTotalStock(),
                product.getStatus(),
                optionResponses,
                product.getCreatedAt()
        );
    }

    /**
     * 상품과 주문 수량을 임시로 보관하는 내부 클래스
     * Application 계층에서 Domain과 Infrastructure 계층의 데이터를 조합할 때 사용
     */
    private static class ProductWithOrderCount {
        final Product product;
        final Long orderCount3Days;

        ProductWithOrderCount(Product product, Long orderCount3Days) {
            this.product = product;
            this.orderCount3Days = orderCount3Days;
        }
    }

    /**
     * 정렬 적용
     *
     * @param products 정렬할 상품 목록
     * @param sort 정렬 기준 (필드명,방향)
     * @return 정렬된 상품 목록
     */
    private List<Product> applySorting(List<Product> products, String sort) {
        String[] sortParts = sort.split(",");
        String sortField = sortParts[0];
        String sortDirection = sortParts.length > 1 ? sortParts[1] : "desc";

        // 유효하지 않은 정렬 필드 검증
        Comparator<Product> comparator = switch (sortField) {
            case "product_id" -> Comparator.comparing(Product::getProductId);
            case "product_name" -> Comparator.comparing(Product::getProductName);
            case "price" -> Comparator.comparing(Product::getPrice);
            case "created_at" -> Comparator.comparing(Product::getCreatedAt);
            default -> throw new IllegalArgumentException("유효하지 않은 정렬 필드입니다: " + sortField);
        };

        if ("asc".equalsIgnoreCase(sortDirection)) {
            return products.stream()
                    .sorted(comparator)
                    .collect(Collectors.toList());
        } else {
            return products.stream()
                    .sorted(comparator.reversed())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Product를 ProductResponse로 변환
     */
    private ProductResponse convertToProductResponse(Product product) {
        return new ProductResponse(
                product.getProductId(),
                product.getProductName(),
                product.getDescription(),
                product.getPrice(),
                product.getTotalStock(),
                product.getStatus(),
                product.getCreatedAt()
        );
    }

    /**
     * ProductOption을 ProductOptionResponse로 변환
     */
    private ProductOptionResponse convertToProductOptionResponse(ProductOption option) {
        return new ProductOptionResponse(
                option.getOptionId(),
                option.getName(),
                option.getStock(),
                option.getVersion()
        );
    }
}