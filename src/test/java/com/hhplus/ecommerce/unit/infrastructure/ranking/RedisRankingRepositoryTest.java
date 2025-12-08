package com.hhplus.ecommerce.unit.infrastructure.ranking;

import com.hhplus.ecommerce.infrastructure.ranking.RedisRankingRepository;
import com.hhplus.ecommerce.domain.ranking.RankingItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RedisRankingRepository 단위 테스트
 *
 * Mock 전략:
 * - RedisTemplate 및 ZSetOperations을 Mock으로 처리
 * - Repository의 비즈니스 로직만 테스트
 * - Redis 연결은 실제로 하지 않음
 *
 * 테스트 범위:
 * - ZADD 명령 호출 확인
 * - ZRANGE 명령 결과 처리
 * - ZREVRANK 명령 결과 처리
 * - ZSCORE 명령 결과 처리
 * - DEL 명령 호출 확인
 * - 예외 처리
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisRankingRepository 단위 테스트")
public class RedisRankingRepositoryTest {

    private RedisRankingRepository rankingRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private static final String TEST_DATE = "20241202";
    private static final String RANKING_KEY = "ranking:daily:" + TEST_DATE;

    @BeforeEach
    void setUp() {
        rankingRepository = new RedisRankingRepository(redisTemplate);
    }

    private void setupZSetMock() {
        // RedisTemplate이 ZSetOperations을 반환하도록 설정
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    // ========== incrementProductScore 테스트 ==========

    @Test
    @DisplayName("상품 점수 증가 - 정상 동작")
    void testIncrementProductScore_Success() {
        // Given
        setupZSetMock();
        Long productId = 100L;

        // When
        rankingRepository.incrementProductScore(TEST_DATE, productId);

        // Then: incrementScore가 호출되었는지 확인
        verify(zSetOperations, times(1))
                .incrementScore(RANKING_KEY, "100", 1.0);
    }

    @Test
    @DisplayName("상품 점수 증가 - 여러 상품")
    void testIncrementProductScore_MultipleProducts() {
        // Given
        setupZSetMock();
        Long product1 = 100L;
        Long product2 = 200L;

        // When
        rankingRepository.incrementProductScore(TEST_DATE, product1);
        rankingRepository.incrementProductScore(TEST_DATE, product2);

        // Then
        verify(zSetOperations).incrementScore(RANKING_KEY, "100", 1.0);
        verify(zSetOperations).incrementScore(RANKING_KEY, "200", 1.0);
    }

    // ========== getTopProducts 테스트 ==========

    @Test
    @DisplayName("TOP N 상품 조회 - 정상 동작")
    void testGetTopProducts_Success() {
        // Given: Mock 데이터 설정
        setupZSetMock();
        Set<ZSetOperations.TypedTuple<String>> mockResults = new LinkedHashSet<>();
        mockResults.add(new MockTypedTuple("100", 150.0)); // productId=100, score=150
        mockResults.add(new MockTypedTuple("200", 120.0)); // productId=200, score=120
        mockResults.add(new MockTypedTuple("300", 100.0)); // productId=300, score=100

        when(zSetOperations.reverseRangeWithScores(RANKING_KEY, 0, 4))
                .thenReturn(mockResults);

        // When
        List<RankingItem> topProducts = rankingRepository.getTopProducts(TEST_DATE, 5);

        // Then
        assertEquals(3, topProducts.size());
        assertEquals(100L, topProducts.get(0).getProductId());
        assertEquals(150L, topProducts.get(0).getScore());
        assertEquals(200L, topProducts.get(1).getProductId());
        assertEquals(120L, topProducts.get(1).getScore());
        verify(zSetOperations).reverseRangeWithScores(RANKING_KEY, 0, 4);
    }

    @Test
    @DisplayName("TOP N 상품 조회 - 결과 없음")
    void testGetTopProducts_EmptyResult() {
        // Given: 빈 결과
        setupZSetMock();
        when(zSetOperations.reverseRangeWithScores(RANKING_KEY, 0, 4))
                .thenReturn(null);

        // When
        List<RankingItem> topProducts = rankingRepository.getTopProducts(TEST_DATE, 5);

        // Then
        assertTrue(topProducts.isEmpty());
    }

    @Test
    @DisplayName("TOP N 상품 조회 - 범위 확인")
    void testGetTopProducts_RangeParameter() {
        // Given
        setupZSetMock();
        when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(new HashSet<>());

        // When
        rankingRepository.getTopProducts(TEST_DATE, 10);

        // Then: 올바른 범위(0 ~ 9)로 호출되었는지 확인
        verify(zSetOperations).reverseRangeWithScores(RANKING_KEY, 0, 9);
    }

    // ========== getProductRank 테스트 ==========

    @Test
    @DisplayName("상품 순위 조회 - 정상 동작")
    void testGetProductRank_Success() {
        // Given: 순위 3 반환 (0부터 시작이므로 실제 순위는 4)
        setupZSetMock();
        when(zSetOperations.reverseRank(RANKING_KEY, "100"))
                .thenReturn(2L);

        // When
        Optional<Long> rank = rankingRepository.getProductRank(TEST_DATE, 100L);

        // Then
        assertTrue(rank.isPresent());
        assertEquals(3L, rank.get()); // 0 + 1 = 3
    }

    @Test
    @DisplayName("상품 순위 조회 - 1등")
    void testGetProductRank_FirstPlace() {
        // Given: 순위 0 (1등)
        setupZSetMock();
        when(zSetOperations.reverseRank(RANKING_KEY, "100"))
                .thenReturn(0L);

        // When
        Optional<Long> rank = rankingRepository.getProductRank(TEST_DATE, 100L);

        // Then
        assertTrue(rank.isPresent());
        assertEquals(1L, rank.get());
    }

    @Test
    @DisplayName("상품 순위 조회 - 존재하지 않음")
    void testGetProductRank_NotFound() {
        // Given: null 반환 (존재하지 않음)
        setupZSetMock();
        when(zSetOperations.reverseRank(RANKING_KEY, "999"))
                .thenReturn(null);

        // When
        Optional<Long> rank = rankingRepository.getProductRank(TEST_DATE, 999L);

        // Then
        assertFalse(rank.isPresent());
    }

    // ========== getProductScore 테스트 ==========

    @Test
    @DisplayName("상품 점수 조회 - 정상 동작")
    void testGetProductScore_Success() {
        // Given
        setupZSetMock();
        when(zSetOperations.score(RANKING_KEY, "100"))
                .thenReturn(150.0);

        // When
        Long score = rankingRepository.getProductScore(TEST_DATE, 100L);

        // Then
        assertEquals(150L, score);
    }

    @Test
    @DisplayName("상품 점수 조회 - 0점")
    void testGetProductScore_ZeroScore() {
        // Given
        setupZSetMock();
        when(zSetOperations.score(RANKING_KEY, "100"))
                .thenReturn(null);

        // When
        Long score = rankingRepository.getProductScore(TEST_DATE, 100L);

        // Then
        assertEquals(0L, score);
    }

    // ========== resetDailyRanking 테스트 ==========

    @Test
    @DisplayName("일일 랭킹 초기화")
    void testResetDailyRanking_Success() {
        // Given: zSetOperations를 사용하지 않으므로 setupZSetMock() 호출 안 함
        when(redisTemplate.delete(RANKING_KEY))
                .thenReturn(true);

        // When
        rankingRepository.resetDailyRanking(TEST_DATE);

        // Then
        verify(redisTemplate).delete(RANKING_KEY);
    }

    @Test
    @DisplayName("일일 랭킹 초기화 - 데이터 없음")
    void testResetDailyRanking_NoData() {
        // Given: zSetOperations를 사용하지 않으므로 setupZSetMock() 호출 안 함
        when(redisTemplate.delete(RANKING_KEY))
                .thenReturn(false);

        // When
        rankingRepository.resetDailyRanking(TEST_DATE);

        // Then
        verify(redisTemplate).delete(RANKING_KEY);
    }

    // ========== 예외 처리 테스트 ==========

    @Test
    @DisplayName("incrementProductScore 예외 처리")
    void testIncrementProductScore_Exception() {
        // Given: 예외 발생
        setupZSetMock();
        when(zSetOperations.incrementScore(anyString(), anyString(), anyDouble()))
                .thenThrow(new RuntimeException("Redis 연결 실패"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            rankingRepository.incrementProductScore(TEST_DATE, 100L);
        });
    }

    @Test
    @DisplayName("getTopProducts 예외 처리")
    void testGetTopProducts_Exception() {
        // Given
        setupZSetMock();
        when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("Redis 조회 실패"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            rankingRepository.getTopProducts(TEST_DATE, 5);
        });
    }

    @Test
    @DisplayName("getProductRank 예외 처리")
    void testGetProductRank_Exception() {
        // Given
        setupZSetMock();
        when(zSetOperations.reverseRank(anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis 조회 실패"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            rankingRepository.getProductRank(TEST_DATE, 100L);
        });
    }

    @Test
    @DisplayName("getProductScore 예외 처리")
    void testGetProductScore_Exception() {
        // Given
        setupZSetMock();
        when(zSetOperations.score(anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis 조회 실패"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            rankingRepository.getProductScore(TEST_DATE, 100L);
        });
    }

    // ========== Mock 헬퍼 클래스 ==========

    /**
     * ZSetOperations.TypedTuple의 Mock 구현
     */
    private static class MockTypedTuple implements ZSetOperations.TypedTuple<String> {
        private final String value;
        private final Double score;

        MockTypedTuple(String value, Double score) {
            this.value = value;
            this.score = score;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public Double getScore() {
            return score;
        }

        @Override
        public int compareTo(ZSetOperations.TypedTuple<String> o) {
            if (o == null || o.getScore() == null) {
                return 1;
            }
            return Double.compare(score != null ? score : 0, o.getScore());
        }
    }
}
