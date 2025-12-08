package com.hhplus.ecommerce.unit.application.ranking;

import com.hhplus.ecommerce.application.ranking.RankingServiceImpl;
import com.hhplus.ecommerce.domain.ranking.RankingRepository;
import com.hhplus.ecommerce.domain.ranking.RankingItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RankingService 단위 테스트
 *
 * 테스트 범위:
 * - 상품 점수 증가 (주문 발생)
 * - TOP N 상품 조회
 * - 특정 상품 순위 확인
 * - 상품 주문 수 조회
 * - 예외 처리 및 입력 검증
 *
 * Mock 대상:
 * - RankingRepository (Redis 접근 계층)
 *
 * 테스트 전략:
 * - Mock을 통해 Repository 메서드 동작 시뮬레이션
 * - Service 계층의 비즈니스 로직 검증
 * - 날짜 처리 및 타입 변환 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RankingService 단위 테스트")
public class RankingServiceTest {

    private RankingServiceImpl rankingService;

    @Mock
    private RankingRepository rankingRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String TODAY_DATE = LocalDate.now().format(DATE_FORMATTER);

    @BeforeEach
    void setUp() {
        rankingService = new RankingServiceImpl(rankingRepository);
    }

    // ========== 상품 점수 증가 (주문 발생) ==========

    @Test
    @DisplayName("주문 발생 시 상품 점수 1 증가")
    void testIncrementProductScore_Success() {
        // Given
        Long productId = 100L;

        // When
        rankingService.incrementProductScore(productId);

        // Then
        verify(rankingRepository, times(1))
                .incrementProductScore(TODAY_DATE, productId);
    }

    @Test
    @DisplayName("주문 발생 시 점수 여러 번 증가 (동시 주문)")
    void testIncrementProductScore_MultipleOrders() {
        // Given
        Long productId = 100L;

        // When: 같은 상품이 3번 주문됨
        rankingService.incrementProductScore(productId);
        rankingService.incrementProductScore(productId);
        rankingService.incrementProductScore(productId);

        // Then: Repository의 incrementProductScore가 3번 호출됨
        verify(rankingRepository, times(3))
                .incrementProductScore(TODAY_DATE, productId);
    }

    @Test
    @DisplayName("서로 다른 상품의 점수 증가")
    void testIncrementProductScore_DifferentProducts() {
        // Given
        Long product1 = 100L;
        Long product2 = 200L;
        Long product3 = 300L;

        // When: 서로 다른 상품 주문
        rankingService.incrementProductScore(product1);
        rankingService.incrementProductScore(product2);
        rankingService.incrementProductScore(product3);

        // Then: 각 상품마다 한 번씩 호출
        verify(rankingRepository).incrementProductScore(TODAY_DATE, product1);
        verify(rankingRepository).incrementProductScore(TODAY_DATE, product2);
        verify(rankingRepository).incrementProductScore(TODAY_DATE, product3);
    }

    // ========== TOP N 상품 조회 ==========

    @Test
    @DisplayName("TOP 5 상품 조회 - 정상")
    void testGetTopProducts_Success() {
        // Given: TOP 5 상품 데이터 준비
        List<RankingItem> topProducts = List.of(
                RankingItem.builder().productId(100L).score(150L).build(),
                RankingItem.builder().productId(200L).score(120L).build(),
                RankingItem.builder().productId(300L).score(100L).build(),
                RankingItem.builder().productId(400L).score(80L).build(),
                RankingItem.builder().productId(500L).score(50L).build()
        );
        when(rankingRepository.getTopProducts(TODAY_DATE, 5))
                .thenReturn(topProducts);

        // When
        List<RankingItem> result = rankingService.getTopProducts(5);

        // Then
        assertEquals(5, result.size());
        assertEquals(100L, result.get(0).getProductId());
        assertEquals(150L, result.get(0).getScore());
        assertEquals(500L, result.get(4).getProductId());
        assertEquals(50L, result.get(4).getScore());
        verify(rankingRepository, times(1)).getTopProducts(TODAY_DATE, 5);
    }

    @Test
    @DisplayName("TOP 10 상품 조회")
    void testGetTopProducts_Top10() {
        // Given: 상위 10개 조회
        List<RankingItem> topProducts = List.of(
                RankingItem.builder().productId(1L).score(100L).build(),
                RankingItem.builder().productId(2L).score(90L).build(),
                RankingItem.builder().productId(3L).score(80L).build()
        );
        when(rankingRepository.getTopProducts(TODAY_DATE, 10))
                .thenReturn(topProducts);

        // When
        List<RankingItem> result = rankingService.getTopProducts(10);

        // Then: 실제로는 3개만 있음 (10개 미만)
        assertEquals(3, result.size());
        verify(rankingRepository).getTopProducts(TODAY_DATE, 10);
    }

    @Test
    @DisplayName("TOP 상품 조회 - 랭킹 데이터 없음")
    void testGetTopProducts_EmptyRanking() {
        // Given: 빈 리스트 반환
        when(rankingRepository.getTopProducts(TODAY_DATE, 5))
                .thenReturn(List.of());

        // When
        List<RankingItem> result = rankingService.getTopProducts(5);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("TOP 상품 조회 - topN <= 0 검증")
    void testGetTopProducts_InvalidTopN() {
        // Given & When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            rankingService.getTopProducts(0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            rankingService.getTopProducts(-5);
        });

        verify(rankingRepository, never()).getTopProducts(anyString(), anyLong());
    }

    // ========== 특정 상품 순위 확인 ==========

    @Test
    @DisplayName("특정 상품의 순위 조회 - 정상")
    void testGetProductRank_Success() {
        // Given: 상품 ID 100이 3등
        when(rankingRepository.getProductRank(TODAY_DATE, 100L))
                .thenReturn(Optional.of(3L));

        // When
        Optional<Long> rank = rankingService.getProductRank(100L);

        // Then
        assertTrue(rank.isPresent());
        assertEquals(3L, rank.get());
        verify(rankingRepository).getProductRank(TODAY_DATE, 100L);
    }

    @Test
    @DisplayName("특정 상품의 순위 조회 - 1등")
    void testGetProductRank_FirstPlace() {
        // Given: 상품 ID 100이 1등
        when(rankingRepository.getProductRank(TODAY_DATE, 100L))
                .thenReturn(Optional.of(1L));

        // When
        Optional<Long> rank = rankingService.getProductRank(100L);

        // Then
        assertTrue(rank.isPresent());
        assertEquals(1L, rank.get());
    }

    @Test
    @DisplayName("특정 상품의 순위 조회 - 랭킹 없음")
    void testGetProductRank_NotInRanking() {
        // Given: 상품이 랭킹에 없음
        when(rankingRepository.getProductRank(TODAY_DATE, 999L))
                .thenReturn(Optional.empty());

        // When
        Optional<Long> rank = rankingService.getProductRank(999L);

        // Then
        assertFalse(rank.isPresent());
    }

    @Test
    @DisplayName("특정 상품 순위 조회 - 유효하지 않은 상품 ID")
    void testGetProductRank_InvalidProductId() {
        // Given & When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            rankingService.getProductRank(null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            rankingService.getProductRank(0L);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            rankingService.getProductRank(-100L);
        });

        verify(rankingRepository, never()).getProductRank(anyString(), anyLong());
    }

    // ========== 상품 주문 수 조회 ==========

    @Test
    @DisplayName("상품의 주문 수 조회 - 정상")
    void testGetProductScore_Success() {
        // Given: 상품 ID 100의 주문 수는 150
        when(rankingRepository.getProductScore(TODAY_DATE, 100L))
                .thenReturn(150L);

        // When
        Long score = rankingService.getProductScore(100L);

        // Then
        assertEquals(150L, score);
        verify(rankingRepository).getProductScore(TODAY_DATE, 100L);
    }

    @Test
    @DisplayName("상품의 주문 수 조회 - 주문 없음")
    void testGetProductScore_NoOrders() {
        // Given: 상품이 랭킹에 없으면 0 반환
        when(rankingRepository.getProductScore(TODAY_DATE, 999L))
                .thenReturn(0L);

        // When
        Long score = rankingService.getProductScore(999L);

        // Then
        assertEquals(0L, score);
    }

    @Test
    @DisplayName("상품 주문 수 조회 - 유효하지 않은 상품 ID")
    void testGetProductScore_InvalidProductId() {
        // Given & When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            rankingService.getProductScore(null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            rankingService.getProductScore(0L);
        });

        verify(rankingRepository, never()).getProductScore(anyString(), anyLong());
    }

    // ========== 예외 처리 ==========

    @Test
    @DisplayName("Repository 예외 발생 시 처리 - incrementProductScore")
    void testIncrementProductScore_RepositoryException() {
        // Given: Repository에서 예외 발생
        doThrow(new RuntimeException("Redis 연결 실패"))
                .when(rankingRepository).incrementProductScore(anyString(), anyLong());

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            rankingService.incrementProductScore(100L);
        });
    }

    @Test
    @DisplayName("Repository 예외 발생 시 처리 - getTopProducts")
    void testGetTopProducts_RepositoryException() {
        // Given: Repository에서 예외 발생
        when(rankingRepository.getTopProducts(TODAY_DATE, 5))
                .thenThrow(new RuntimeException("Redis 조회 실패"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            rankingService.getTopProducts(5);
        });
    }

    @Test
    @DisplayName("Repository 예외 발생 시 처리 - getProductRank")
    void testGetProductRank_RepositoryException() {
        // Given: Repository에서 예외 발생
        when(rankingRepository.getProductRank(TODAY_DATE, 100L))
                .thenThrow(new RuntimeException("Redis 조회 실패"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            rankingService.getProductRank(100L);
        });
    }

    @Test
    @DisplayName("Repository 예외 발생 시 처리 - getProductScore")
    void testGetProductScore_RepositoryException() {
        // Given: Repository에서 예외 발생
        when(rankingRepository.getProductScore(TODAY_DATE, 100L))
                .thenThrow(new RuntimeException("Redis 조회 실패"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            rankingService.getProductScore(100L);
        });
    }
}
