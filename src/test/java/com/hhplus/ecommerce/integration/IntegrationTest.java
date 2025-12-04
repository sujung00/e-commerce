package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.application.inventory.InventoryService;
import com.hhplus.ecommerce.application.order.OrderService;
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
 * 통합테스트 - TestContainers MySQL 기반 통합 테스트
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
@DisplayName("MySQL 기반 통합 테스트")
class IntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CouponService 쿠폰서비스;

    @Autowired
    private OrderService 주문서비스;

    @Autowired
    private InventoryService 재고서비스;

    @Autowired
    private CouponRepository 쿠폰저장소;

    @Autowired
    private UserRepository 사용자저장소;

    @Autowired
    private ProductRepository 상품저장소;

    @Autowired
    private EntityManager 엔티티매니저;

    private User 테스트사용자;
    private Product 테스트상품;
    private Coupon 테스트쿠폰;
    private ProductOption 옵션1;
    private ProductOption 옵션2;

    // ========== 테스트 데이터 설정 ==========

    private void 테스트데이터생성() {
        // 사용자 생성 - ID는 자동 생성되도록 제거
        테스트사용자 = User.builder()
                .email("test@test.com")
                .name("테스트 사용자")
                .balance(100000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        사용자저장소.save(테스트사용자);
        엔티티매니저.flush();

        // 상품 먼저 생성하여 ID 확보
        테스트상품 = Product.builder()
                .productName("통합 테스트 상품")
                .description("이것은 통합 테스트용 상품입니다")
                .price(30000L)
                .totalStock(30)
                .status("판매 중")
                .options(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        상품저장소.save(테스트상품);
        엔티티매니저.flush();

        // 상품 ID를 사용하여 옵션 생성
        ProductOption 임시옵션1 = ProductOption.builder()
                .productId(테스트상품.getProductId())
                .name("Red")
                .stock(10)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        상품저장소.saveOption(임시옵션1);
        엔티티매니저.flush();

        // Blue 옵션 추가 생성
        ProductOption 임시옵션2 = ProductOption.builder()
                .productId(테스트상품.getProductId())
                .name("Blue")
                .stock(20)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        상품저장소.saveOption(임시옵션2);
        엔티티매니저.flush();

        // 옵션 ID를 얻기 위해 DB에서 조회
        List<ProductOption> 옵션목록 = 상품저장소.findOptionsByProductId(테스트상품.getProductId());
        if (옵션목록.size() < 2) {
            throw new RuntimeException("옵션 저장 실패: 옵션이 2개 이상 필요합니다: " + 옵션목록.size());
        }

        옵션1 = 옵션목록.get(0);  // Red
        옵션2 = 옵션목록.get(1);  // Blue

        // Product의 options 컬렉션에 옵션 추가 (OrderValidator에서 사용)
        테스트상품.getOptions().clear();
        테스트상품.getOptions().addAll(옵션목록);

        // 쿠폰 생성 - ID는 자동 생성되도록 제거
        테스트쿠폰 = Coupon.builder()
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
        쿠폰저장소.save(테스트쿠폰);
    }

    @BeforeEach
    void 준비() {
        테스트데이터생성();
    }

    // ========== 쿠폰 통합 테스트 ==========

    @Test
    @DisplayName("쿠폰 발급 - 성공한다")
    void 쿠폰발급_성공한다() {
        // When
        IssueCouponResponse 응답 = 쿠폰서비스.issueCoupon(테스트사용자.getUserId(), 테스트쿠폰.getCouponId());

        // Then
        assertNotNull(응답);
        assertEquals(테스트쿠폰.getCouponId(), 응답.getCouponId());
        assertEquals(테스트쿠폰.getCouponName(), 응답.getCouponName());

        // 쿠폰 수량 확인
        Coupon 업데이트됨 = 쿠폰저장소.findById(테스트쿠폰.getCouponId()).orElseThrow();
        assertEquals(4, 업데이트됨.getRemainingQty(),
                "쿠폰 수량이 5에서 4로 감소해야 합니다");
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패한다 (중복 발급)")
    void 쿠폰발급_실패한다_중복발급() {
        // When - 첫 발급
        쿠폰서비스.issueCoupon(테스트사용자.getUserId(), 테스트쿠폰.getCouponId());

        // When - 두 번째 발급 시도
        assertThrows(IllegalArgumentException.class,
                () -> 쿠폰서비스.issueCoupon(테스트사용자.getUserId(), 테스트쿠폰.getCouponId()),
                "같은 사용자는 같은 쿠폰을 두 번 받을 수 없습니다");
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패한다 (재고 부족)")
    void 쿠폰발급_실패한다_재고부족() {
        // Given - 쿠폰 수량을 1로 설정
        테스트쿠폰.setRemainingQty(1);
        쿠폰저장소.update(테스트쿠폰);

        // 첫 번째 사용자 발급
        User 사용자1 = User.builder()
                .email("user1_" + System.currentTimeMillis() + "@test.com")
                .name("사용자1")
                .balance(10000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        사용자저장소.save(사용자1);
        쿠폰서비스.issueCoupon(사용자1.getUserId(), 테스트쿠폰.getCouponId());

        // 두 번째 사용자 발급 시도
        User 사용자2 = User.builder()
                .email("user2_" + System.currentTimeMillis() + "@test.com")
                .name("사용자2")
                .balance(10000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        사용자저장소.save(사용자2);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> 쿠폰서비스.issueCoupon(사용자2.getUserId(), 테스트쿠폰.getCouponId()),
                "쿠폰이 모두 소진되면 발급할 수 없습니다");
    }

    // ========== 재고 통합 테스트 ==========

    @Test
    @DisplayName("재고 조회 - 성공한다")
    void 재고조회_성공한다() {
        // When
        InventoryResponse 응답 = 재고서비스.getProductInventory(테스트상품.getProductId());

        // Then
        assertNotNull(응답);
        assertEquals(테스트상품.getProductId(), 응답.getProductId());
        assertEquals(테스트상품.getProductName(), 응답.getProductName());
        assertEquals(30, 응답.getTotalStock());
        assertEquals(2, 응답.getOptions().size());
    }

    @Test
    @DisplayName("재고 조회 - 옵션별 재고 상세정보")
    void 재고조회_옵션별상세() {
        // When
        InventoryResponse 응답 = 재고서비스.getProductInventory(테스트상품.getProductId());

        // Then
        assertEquals(2, 응답.getOptions().size());
        assertEquals("Red", 응답.getOptions().get(0).getName());
        assertEquals(10, 응답.getOptions().get(0).getStock());
        assertEquals("Blue", 응답.getOptions().get(1).getName());
        assertEquals(20, 응답.getOptions().get(1).getStock());
    }

    @Test
    @DisplayName("재고 조회 - 실패한다 (존재하지 않는 상품)")
    void 재고조회_실패한다_상품없음() {
        // When & Then
        assertThrows(Exception.class,
                () -> 재고서비스.getProductInventory(9999L),
                "존재하지 않는 상품은 조회할 수 없습니다");
    }

    // ========== 주문 통합 테스트 ==========

    @Test
    @DisplayName("주문 생성 - 성공한다 (쿠폰 미적용)")
    void 주문생성_성공한다_쿠폰없음() {
        // Given
        OrderItemCommand 주문항목 = OrderItemCommand.builder()
                .productId(테스트상품.getProductId())
                .optionId(옵션1.getOptionId())
                .quantity(2)
                .build();

        CreateOrderCommand 명령어 = CreateOrderCommand.builder()
                .orderItems(List.of(주문항목))
                .couponId(null)
                .build();

        // When
        CreateOrderResponse 응답 = 주문서비스.createOrder(테스트사용자.getUserId(), 명령어);

        // Then
        assertNotNull(응답);
        assertNotNull(응답.getOrderId());
    }

    @Test
    @DisplayName("주문 생성 - 성공한다 (쿠폰 적용)")
    void 주문생성_성공한다_쿠폰적용() {
        // Given - 먼저 쿠폰 발급
        쿠폰서비스.issueCoupon(테스트사용자.getUserId(), 테스트쿠폰.getCouponId());

        OrderItemCommand 주문항목 = OrderItemCommand.builder()
                .productId(테스트상품.getProductId())
                .optionId(옵션1.getOptionId())
                .quantity(1)
                .build();

        CreateOrderCommand 명령어 = CreateOrderCommand.builder()
                .orderItems(List.of(주문항목))
                .couponId(테스트쿠폰.getCouponId())
                .build();

        // When
        CreateOrderResponse 응답 = 주문서비스.createOrder(테스트사용자.getUserId(), 명령어);

        // Then
        assertNotNull(응답);
        assertTrue(응답.getFinalAmount() < 응답.getSubtotal(),
                "쿠폰 할인이 적용되어 최종 금액이 소계보다 작아야 합니다");
    }

    @Test
    @DisplayName("주문 생성 - 실패한다 (재고 부족)")
    void 주문생성_실패한다_재고부족() {
        // Given - 재고보다 많은 수량 주문
        OrderItemCommand 주문항목 = OrderItemCommand.builder()
                .productId(테스트상품.getProductId())
                .optionId(옵션1.getOptionId())
                .quantity(100)  // 재고는 10개
                .build();

        CreateOrderCommand 명령어 = CreateOrderCommand.builder()
                .orderItems(List.of(주문항목))
                .couponId(null)
                .build();

        // When & Then
        assertThrows(Exception.class,
                () -> 주문서비스.createOrder(테스트사용자.getUserId(), 명령어),
                "재고가 부족하면 주문할 수 없습니다");
    }

    @Test
    @DisplayName("주문 생성 - 실패한다 (잔액 부족)")
    void 주문생성_실패한다_잔액부족() {
        // Given - 잔액 부족한 사용자
        User 빈곤사용자 = User.builder()
                .email("poor@test.com")
                .name("빈곤한 사용자")
                .balance(1000L)  // 상품 가격은 30,000원
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        사용자저장소.save(빈곤사용자);

        OrderItemCommand 주문항목 = OrderItemCommand.builder()
                .productId(테스트상품.getProductId())
                .optionId(옵션1.getOptionId())
                .quantity(1)
                .build();

        CreateOrderCommand 명령어 = CreateOrderCommand.builder()
                .orderItems(List.of(주문항목))
                .couponId(null)
                .build();

        // When & Then
        assertThrows(Exception.class,
                () -> 주문서비스.createOrder(빈곤사용자.getUserId(), 명령어),
                "잔액이 부족하면 주문할 수 없습니다");
    }

    // ========== 엔드-투-엔드 시나리오 ==========

    @Test
    @DisplayName("엔드투엔드: 상품조회 → 쿠폰발급 → 주문생성")
    void 엔드투엔드_상품조회_쿠폰발급_주문생성() {
        // Step 1: 상품 및 재고 조회
        InventoryResponse 재고정보 = 재고서비스.getProductInventory(테스트상품.getProductId());
        assertTrue(재고정보.getTotalStock() > 0, "상품은 판매 가능한 재고가 있어야 합니다");

        // Step 2: 쿠폰 발급
        IssueCouponResponse 쿠폰 = 쿠폰서비스.issueCoupon(테스트사용자.getUserId(), 테스트쿠폰.getCouponId());
        assertNotNull(쿠폰.getUserCouponId(), "쿠폰이 발급되어야 합니다");

        // Step 3: 주문 생성
        OrderItemCommand 주문항목 = OrderItemCommand.builder()
                .productId(테스트상품.getProductId())
                .optionId(옵션1.getOptionId())
                .quantity(2)
                .build();

        CreateOrderCommand 명령어 = CreateOrderCommand.builder()
                .orderItems(List.of(주문항목))
                .couponId(테스트쿠폰.getCouponId())
                .build();

        CreateOrderResponse 주문 = 주문서비스.createOrder(테스트사용자.getUserId(), 명령어);

        // Then - 모든 단계가 성공해야 함
        assertNotNull(주문.getOrderId(), "주문이 생성되어야 합니다");
        assertTrue(주문.getFinalAmount() > 0, "주문 금액이 있어야 합니다");

        // 재고 확인
        InventoryResponse 업데이트재고 = 재고서비스.getProductInventory(테스트상품.getProductId());
        assertEquals(재고정보.getTotalStock() - 2, 업데이트재고.getTotalStock(),
                "재고가 2개 감소해야 합니다");
    }
}
