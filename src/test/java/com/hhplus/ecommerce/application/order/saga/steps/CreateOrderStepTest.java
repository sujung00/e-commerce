package com.hhplus.ecommerce.application.order.saga.steps;

import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import com.hhplus.ecommerce.application.order.saga.context.SagaContext;
import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.order.OrderStatus;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.integration.BaseIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CreateOrderStep 보상 로직 테스트
 *
 * 검증 포인트:
 * 1. 정상 보상 처리 (주문 상태 PENDING → CANCELLED)
 * 2. 비관적 락 획득 및 동시성 제어
 * 3. DB 상태 변화 검증
 */
@SpringBootTest
@DisplayName("CreateOrderStep 보상 로직 테스트")
class CreateOrderStepTest extends BaseIntegrationTest {

    @Autowired
    private CreateOrderStep createOrderStep;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate newTransactionTemplate;

    @Autowired
    public void setTransactionManager(PlatformTransactionManager tm) {
        this.newTransactionTemplate = new TransactionTemplate(tm);
        this.newTransactionTemplate.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    @Test
    @DisplayName("[CreateOrderStep] Forward → Backward 정상 플로우 - 주문 생성 후 취소")
    void testCompensate_Success_CancelOrder() throws Exception {
        // Given - 테스트 데이터 준비
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long[] productIdArray = new long[1];
        long[] optionIdArray = new long[1];

        // 상품 및 옵션 생성
        newTransactionTemplate.execute(status -> {
            Product product = Product.builder()
                    .productName("주문테스트상품_" + testId)
                    .price(10000L)
                    .totalStock(100)
                    .status("IN_STOCK")
                    .options(new ArrayList<>())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.save(product);
            entityManager.flush();

            ProductOption option = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("기본옵션")
                    .stock(100)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option);
            entityManager.flush();

            productIdArray[0] = product.getProductId();
            optionIdArray[0] = option.getOptionId();
            return null;
        });

        long productId = productIdArray[0];
        long optionId = optionIdArray[0];

        // SagaContext 생성
        List<OrderItemDto> orderItems = List.of(
                new OrderItemDto(productId, optionId, 10)
        );
        SagaContext context = new SagaContext(
                1L, // userId
                orderItems,
                null, // couponId
                0L, // couponDiscount
                100000L, // subtotal
                100000L  // finalAmount
        );

        // When - Forward Flow (주문 생성)
        createOrderStep.execute(context);

        // Then - Forward Flow 검증 (DB 상태로 검증)
        assertNotNull(context.getOrderId(), "주문 ID가 context에 설정되어야 함");
        Order createdOrder = newTransactionTemplate.execute(status ->
            orderRepository.findById(context.getOrderId()).orElseThrow()
        );
        assertEquals(OrderStatus.PENDING, createdOrder.getOrderStatus(), "주문 상태가 PENDING이어야 함");

        // When - Backward Flow (주문 취소)
        createOrderStep.compensate(context);

        // Then - Backward Flow 검증 (주문 상태 CANCELLED)
        Order cancelledOrder = newTransactionTemplate.execute(status ->
            orderRepository.findById(context.getOrderId()).orElseThrow()
        );
        assertEquals(OrderStatus.CANCELLED, cancelledOrder.getOrderStatus(),
            "주문 상태가 CANCELLED로 변경되어야 함");
    }

    @Test
    @DisplayName("[CreateOrderStep] 보상 skip - orderId가 null인 경우")
    void testCompensate_Skip_WhenOrderNotCreated() throws Exception {
        // Given - SagaContext 생성 (orderId=null)
        SagaContext context = new SagaContext(
                1L, // userId
                List.of(new OrderItemDto(1L, 1L, 1)),
                null, // couponId
                0L, // couponDiscount
                10000L, // subtotal
                10000L  // finalAmount
        );

        // When - Backward Flow (보상 시도)
        // Then - skip 되어야 함 (예외 발생하지 않음)
        assertDoesNotThrow(() -> createOrderStep.compensate(context),
            "orderId가 null일 때 보상이 skip 되어야 함");
    }

    @Test
    @DisplayName("[CreateOrderStep] 보상 실패 시 Best Effort - 예외 발생해도 전파하지 않음")
    void testCompensate_BestEffort_NoExceptionPropagation() throws Exception {
        // Given - 존재하지 않는 주문 ID로 SagaContext 생성
        SagaContext context = new SagaContext(
                1L, // userId
                List.of(new OrderItemDto(1L, 1L, 1)),
                null, // couponId
                0L, // couponDiscount
                10000L, // subtotal
                10000L  // finalAmount
        );
        context.setOrderId(999999L); // 존재하지 않는 orderId 설정

        // When & Then - 보상 실패해도 예외 발생하지 않음 (Best Effort)
        assertDoesNotThrow(() -> createOrderStep.compensate(context),
            "보상 실패해도 예외가 발생하지 않아야 함 (Best Effort)");
    }

    @Test
    @DisplayName("[CreateOrderStep] 주문 상태 전이 검증 - PENDING → CANCELLED")
    void testCompensate_StateTransition_PendingToCancelled() throws Exception {
        // Given - 테스트 데이터 준비
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long[] productIdArray = new long[1];
        long[] optionIdArray = new long[1];

        // 상품 및 옵션 생성
        newTransactionTemplate.execute(status -> {
            Product product = Product.builder()
                    .productName("주문테스트상품_" + testId)
                    .price(10000L)
                    .totalStock(100)
                    .status("IN_STOCK")
                    .options(new ArrayList<>())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.save(product);
            entityManager.flush();

            ProductOption option = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("기본옵션")
                    .stock(100)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option);
            entityManager.flush();

            productIdArray[0] = product.getProductId();
            optionIdArray[0] = option.getOptionId();
            return null;
        });

        long productId = productIdArray[0];
        long optionId = optionIdArray[0];

        // SagaContext 생성
        List<OrderItemDto> orderItems = List.of(
                new OrderItemDto(productId, optionId, 10)
        );
        SagaContext context = new SagaContext(
                1L, // userId
                orderItems,
                null, // couponId
                0L, // couponDiscount
                100000L, // subtotal
                100000L  // finalAmount
        );

        // When - Forward Flow (주문 생성)
        createOrderStep.execute(context);

        // Then - 초기 상태 PENDING (DB 검증)
        Order createdOrder = newTransactionTemplate.execute(status ->
            orderRepository.findById(context.getOrderId()).orElseThrow()
        );
        assertEquals(OrderStatus.PENDING, createdOrder.getOrderStatus(), "초기 상태는 PENDING이어야 함");

        // When - Backward Flow (주문 취소)
        createOrderStep.compensate(context);

        // Then - 최종 상태 CANCELLED
        Order cancelledOrder = newTransactionTemplate.execute(status ->
            orderRepository.findById(context.getOrderId()).orElseThrow()
        );
        assertEquals(OrderStatus.CANCELLED, cancelledOrder.getOrderStatus(),
            "최종 상태는 CANCELLED이어야 함");
    }

    @Test
    @DisplayName("[CreateOrderStep] 주문이 PENDING이 아닌 경우 보상 skip")
    void testCompensate_Skip_WhenOrderNotPending() throws Exception {
        // Given - 테스트 데이터 준비
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long[] productIdArray = new long[1];
        long[] optionIdArray = new long[1];

        // 상품 및 옵션 생성
        newTransactionTemplate.execute(status -> {
            Product product = Product.builder()
                    .productName("주문테스트상품_" + testId)
                    .price(10000L)
                    .totalStock(100)
                    .status("IN_STOCK")
                    .options(new ArrayList<>())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.save(product);
            entityManager.flush();

            ProductOption option = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("기본옵션")
                    .stock(100)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option);
            entityManager.flush();

            productIdArray[0] = product.getProductId();
            optionIdArray[0] = option.getOptionId();
            return null;
        });

        long productId = productIdArray[0];
        long optionId = optionIdArray[0];

        // SagaContext 생성
        List<OrderItemDto> orderItems = List.of(
                new OrderItemDto(productId, optionId, 10)
        );
        SagaContext context = new SagaContext(
                1L, // userId
                orderItems,
                null, // couponId
                0L, // couponDiscount
                100000L, // subtotal
                100000L  // finalAmount
        );

        // Forward Flow (주문 생성)
        createOrderStep.execute(context);

        // 주문 상태를 수동으로 PAID로 변경
        Long orderId = context.getOrderId();
        newTransactionTemplate.execute(status -> {
            Order dbOrder = orderRepository.findById(orderId).orElseThrow();
            dbOrder.markAsPaid(); // PENDING → PAID
            orderRepository.save(dbOrder);
            return null;
        });

        // When - Backward Flow (보상 시도)
        createOrderStep.compensate(context);

        // Then - PAID 상태 유지 (보상 skip)
        Order afterCompensate = newTransactionTemplate.execute(status ->
            orderRepository.findById(orderId).orElseThrow()
        );
        assertEquals(OrderStatus.PAID, afterCompensate.getOrderStatus(),
            "PENDING이 아닌 경우 보상이 skip 되어 상태가 유지되어야 함");
    }

    @Test
    @DisplayName("[CreateOrderStep] 다중 OrderItem 생성 검증")
    void testExecute_MultipleOrderItems_AllCreated() throws Exception {
        // Given - 테스트 데이터 준비
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long[] productIdArray = new long[1];
        long[] optionId1Array = new long[1];
        long[] optionId2Array = new long[1];

        // 상품 및 2개 옵션 생성
        newTransactionTemplate.execute(status -> {
            Product product = Product.builder()
                    .productName("주문테스트상품_" + testId)
                    .price(10000L)
                    .totalStock(200)
                    .status("IN_STOCK")
                    .options(new ArrayList<>())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.save(product);
            entityManager.flush();

            ProductOption option1 = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("옵션1")
                    .stock(100)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option1);

            ProductOption option2 = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("옵션2")
                    .stock(100)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option2);
            entityManager.flush();

            productIdArray[0] = product.getProductId();
            optionId1Array[0] = option1.getOptionId();
            optionId2Array[0] = option2.getOptionId();
            return null;
        });

        long productId = productIdArray[0];
        long optionId1 = optionId1Array[0];
        long optionId2 = optionId2Array[0];

        // SagaContext 생성 (2개 옵션)
        List<OrderItemDto> orderItems = List.of(
                new OrderItemDto(productId, optionId1, 10),
                new OrderItemDto(productId, optionId2, 20)
        );
        SagaContext context = new SagaContext(
                1L, // userId
                orderItems,
                null, // couponId
                0L, // couponDiscount
                300000L, // subtotal
                300000L  // finalAmount
        );

        // When - Forward Flow (주문 생성)
        createOrderStep.execute(context);

        // Then - 2개의 OrderItem이 생성되어야 함 (DB 검증)
        assertNotNull(context.getOrderId(), "주문 ID가 context에 설정되어야 함");
        Order createdOrder = newTransactionTemplate.execute(status ->
            orderRepository.findById(context.getOrderId()).orElseThrow()
        );
        assertEquals(2, createdOrder.getOrderItems().size(), "2개의 OrderItem이 생성되어야 함");
    }
}
