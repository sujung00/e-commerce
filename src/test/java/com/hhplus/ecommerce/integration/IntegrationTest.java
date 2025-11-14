package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.application.inventory.InventoryService;
import com.hhplus.ecommerce.application.order.OrderService;
import com.hhplus.ecommerce.config.TestContainersInitializer;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse;
import com.hhplus.ecommerce.presentation.inventory.response.InventoryResponse;
import com.hhplus.ecommerce.application.order.dto.CreateOrderCommand;
import com.hhplus.ecommerce.application.order.dto.OrderItemCommand;
import com.hhplus.ecommerce.application.order.dto.CreateOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IntegrationTest - TestContainers MySQL 기반 통합 테스트
 *
 * 테스트 목표:
 * - TestContainers를 사용한 격리된 MySQL 테스트 환경
 * - 쿠폰 발급, 조회 통합 흐름
 * - 주문 생성, 조회 통합 흐름
 * - 재고 조회, 차감 통합 흐름
 * - 엔드-투-엔드 주문 처리 시나리오
 *
 * 설정:
 * - @SpringBootTest: 전체 Spring 애플리케이션 컨텍스트 로드
 * - @ContextConfiguration(initializers = TestContainersInitializer.class): MySQL 컨테이너 자동 설정
 * - @Transactional: 테스트 후 자동 롤백으로 데이터 격리
 * - ddl-auto: create-drop으로 스키마 자동 생성/제거
 */
@SpringBootTest
@Transactional
@DisplayName("MySQL 기반 통합 테스트")
class IntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private Product testProduct;
    private Coupon testCoupon;
    private ProductOption option1;
    private ProductOption option2;

    // ========== 테스트 데이터 설정 ==========

    private void createTestData() {
        // 사용자 생성 - ID는 자동 생성되도록 제거
        testUser = User.builder()
                .email("test@test.com")
                .name("테스트 사용자")
                .balance(100000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(testUser);
        entityManager.flush();

        // 상품 먼저 생성하여 ID 확보
        testProduct = Product.builder()
                .productName("통합 테스트 상품")
                .description("이것은 통합 테스트용 상품입니다")
                .price(30000L)
                .totalStock(30)
                .status("판매 중")
                .options(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.save(testProduct);
        entityManager.flush();

        // 상품 ID를 사용하여 옵션 생성
        ProductOption tempOption1 = ProductOption.builder()
                .productId(testProduct.getProductId())
                .name("Red")
                .stock(10)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.saveOption(tempOption1);
        entityManager.flush();

        // Blue 옵션 추가 생성
        ProductOption tempOption2 = ProductOption.builder()
                .productId(testProduct.getProductId())
                .name("Blue")
                .stock(20)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.saveOption(tempOption2);
        entityManager.flush();

        // 옵션 ID를 얻기 위해 DB에서 조회
        List<ProductOption> options = productRepository.findOptionsByProductId(testProduct.getProductId());
        if (options.size() < 2) {
            throw new RuntimeException("옵션 저장 실패: 옵션이 2개 이상 필요합니다: " + options.size());
        }

        option1 = options.get(0);  // Red
        option2 = options.get(1);  // Blue

        // Product의 options 컬렉션에 옵션 추가 (OrderValidator에서 사용)
        testProduct.getOptions().clear();
        testProduct.getOptions().addAll(options);

        // 쿠폰 생성 - ID는 자동 생성되도록 제거
        testCoupon = Coupon.builder()
                .couponName("통합 테스트 쿠폰")
                .description("통합 테스트용 할인 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .isActive(true)
                .totalQuantity(5)
                .remainingQty(5)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        couponRepository.save(testCoupon);
    }

    @BeforeEach
    void setup() {
        createTestData();
    }

    // ========== 쿠폰 통합 테스트 ==========

    @Test
    @DisplayName("쿠폰 발급 - 성공")
    void testCouponIssuance_Success() {
        // When
        IssueCouponResponse response = couponService.issueCoupon(testUser.getUserId(), testCoupon.getCouponId());

        // Then
        assertNotNull(response);
        assertEquals(testCoupon.getCouponId(), response.getCouponId());
        assertEquals(testCoupon.getCouponName(), response.getCouponName());

        // 쿠폰 수량 확인
        Coupon updated = couponRepository.findById(testCoupon.getCouponId()).orElseThrow();
        assertEquals(4, updated.getRemainingQty(),
                "쿠폰 수량이 5에서 4로 감소해야 합니다");
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (중복 발급)")
    void testCouponIssuance_Failed_Duplicate() {
        // When - 첫 발급
        couponService.issueCoupon(testUser.getUserId(), testCoupon.getCouponId());

        // When - 두 번째 발급 시도
        assertThrows(IllegalArgumentException.class,
                () -> couponService.issueCoupon(testUser.getUserId(), testCoupon.getCouponId()),
                "같은 사용자는 같은 쿠폰을 두 번 받을 수 없습니다");
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (재고 부족)")
    void testCouponIssuance_Failed_OutOfStock() {
        // Given - 쿠폰 수량을 1로 설정
        testCoupon.setRemainingQty(1);
        couponRepository.update(testCoupon);

        // 첫 번째 사용자 발급
        User user1 = User.builder()
                .email("user1_" + System.currentTimeMillis() + "@test.com")
                .name("사용자1")
                .balance(10000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(user1);
        couponService.issueCoupon(user1.getUserId(), testCoupon.getCouponId());

        // 두 번째 사용자 발급 시도
        User user2 = User.builder()
                .email("user2_" + System.currentTimeMillis() + "@test.com")
                .name("사용자2")
                .balance(10000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(user2);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> couponService.issueCoupon(user2.getUserId(), testCoupon.getCouponId()),
                "쿠폰이 모두 소진되면 발급할 수 없습니다");
    }

    // ========== 재고 통합 테스트 ==========

    @Test
    @DisplayName("재고 조회 - 성공")
    void testInventoryView_Success() {
        // When
        InventoryResponse response = inventoryService.getProductInventory(testProduct.getProductId());

        // Then
        assertNotNull(response);
        assertEquals(testProduct.getProductId(), response.getProductId());
        assertEquals(testProduct.getProductName(), response.getProductName());
        assertEquals(30, response.getTotalStock());
        assertEquals(2, response.getOptions().size());
    }

    @Test
    @DisplayName("재고 조회 - 옵션별 재고 상세")
    void testInventoryView_OptionDetails() {
        // When
        InventoryResponse response = inventoryService.getProductInventory(testProduct.getProductId());

        // Then
        assertEquals(2, response.getOptions().size());
        assertEquals("Red", response.getOptions().get(0).getName());
        assertEquals(10, response.getOptions().get(0).getStock());
        assertEquals("Blue", response.getOptions().get(1).getName());
        assertEquals(20, response.getOptions().get(1).getStock());
    }

    @Test
    @DisplayName("재고 조회 - 실패 (존재하지 않는 상품)")
    void testInventoryView_Failed_ProductNotFound() {
        // When & Then
        assertThrows(Exception.class,
                () -> inventoryService.getProductInventory(9999L),
                "존재하지 않는 상품은 조회할 수 없습니다");
    }

    // ========== 주문 통합 테스트 ==========

    @Test
    @DisplayName("주문 생성 - 성공 (쿠폰 미적용)")
    void testOrderCreation_Success_NoCoupon() {
        // Given
        OrderItemCommand item = OrderItemCommand.builder()
                .productId(testProduct.getProductId())
                .optionId(option1.getOptionId())
                .quantity(2)
                .build();

        CreateOrderCommand command = CreateOrderCommand.builder()
                .orderItems(List.of(item))
                .couponId(null)
                .build();

        // When
        CreateOrderResponse response = orderService.createOrder(testUser.getUserId(), command);

        // Then
        assertNotNull(response);
        assertNotNull(response.getOrderId());
    }

    @Test
    @DisplayName("주문 생성 - 성공 (쿠폰 적용)")
    void testOrderCreation_Success_WithCoupon() {
        // Given - 먼저 쿠폰 발급
        couponService.issueCoupon(testUser.getUserId(), testCoupon.getCouponId());

        OrderItemCommand item = OrderItemCommand.builder()
                .productId(testProduct.getProductId())
                .optionId(option1.getOptionId())
                .quantity(1)
                .build();

        CreateOrderCommand command = CreateOrderCommand.builder()
                .orderItems(List.of(item))
                .couponId(testCoupon.getCouponId())
                .build();

        // When
        CreateOrderResponse response = orderService.createOrder(testUser.getUserId(), command);

        // Then
        assertNotNull(response);
        assertTrue(response.getFinalAmount() < response.getSubtotal(),
                "쿠폰 할인이 적용되어 최종 금액이 소계보다 작아야 합니다");
    }

    @Test
    @DisplayName("주문 생성 - 실패 (재고 부족)")
    void testOrderCreation_Failed_InsufficientStock() {
        // Given - 재고보다 많은 수량 주문
        OrderItemCommand item = OrderItemCommand.builder()
                .productId(testProduct.getProductId())
                .optionId(option1.getOptionId())
                .quantity(100)  // 재고는 10개
                .build();

        CreateOrderCommand command = CreateOrderCommand.builder()
                .orderItems(List.of(item))
                .couponId(null)
                .build();

        // When & Then
        assertThrows(Exception.class,
                () -> orderService.createOrder(testUser.getUserId(), command),
                "재고가 부족하면 주문할 수 없습니다");
    }

    @Test
    @DisplayName("주문 생성 - 실패 (잔액 부족)")
    void testOrderCreation_Failed_InsufficientBalance() {
        // Given - 잔액 부족한 사용자
        User poorUser = User.builder()
                .email("poor@test.com")
                .name("빈곤한 사용자")
                .balance(1000L)  // 상품 가격은 30,000원
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(poorUser);

        OrderItemCommand item = OrderItemCommand.builder()
                .productId(testProduct.getProductId())
                .optionId(option1.getOptionId())
                .quantity(1)
                .build();

        CreateOrderCommand command = CreateOrderCommand.builder()
                .orderItems(List.of(item))
                .couponId(null)
                .build();

        // When & Then
        assertThrows(Exception.class,
                () -> orderService.createOrder(poorUser.getUserId(), command),
                "잔액이 부족하면 주문할 수 없습니다");
    }

    // ========== 엔드-투-엔드 시나리오 ==========

    @Test
    @DisplayName("엔드-투-엔드: 상품 조회 → 쿠폰 발급 → 주문 생성")
    void testEndToEnd_BrowseProductIssueCouponCreateOrder() {
        // Step 1: 상품 및 재고 조회
        InventoryResponse inventory = inventoryService.getProductInventory(testProduct.getProductId());
        assertTrue(inventory.getTotalStock() > 0, "상품은 판매 가능한 재고가 있어야 합니다");

        // Step 2: 쿠폰 발급
        IssueCouponResponse coupon = couponService.issueCoupon(testUser.getUserId(), testCoupon.getCouponId());
        assertNotNull(coupon.getUserCouponId(), "쿠폰이 발급되어야 합니다");

        // Step 3: 주문 생성
        OrderItemCommand item = OrderItemCommand.builder()
                .productId(testProduct.getProductId())
                .optionId(option1.getOptionId())
                .quantity(2)
                .build();

        CreateOrderCommand command = CreateOrderCommand.builder()
                .orderItems(List.of(item))
                .couponId(testCoupon.getCouponId())
                .build();

        CreateOrderResponse order = orderService.createOrder(testUser.getUserId(), command);

        // Then - 모든 단계가 성공해야 함
        assertNotNull(order.getOrderId(), "주문이 생성되어야 합니다");
        assertTrue(order.getFinalAmount() > 0, "주문 금액이 있어야 합니다");

        // 재고 확인
        InventoryResponse updatedInventory = inventoryService.getProductInventory(testProduct.getProductId());
        assertEquals(inventory.getTotalStock() - 2, updatedInventory.getTotalStock(),
                "재고가 2개 감소해야 합니다");
    }
}
