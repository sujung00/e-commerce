package com.hhplus.ecommerce.infrastructure.config;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import org.hibernate.engine.jdbc.internal.FormatStyle;

/**
 * P6Spy 로그 포맷 커스터마이징 클래스
 *
 * "완성된 SQL + 바인딩된 인자" 형태로 SQL 쿼리를 포매팅하여 로깅합니다.
 * 실행 시간, 쿼리 유형 등 추가 정보도 함께 표시됩니다.
 */
public class P6SpyPrettySqlFormatter implements MessageFormattingStrategy {

    @Override
    public String formatMessage(
        int connectionId,
        String now,
        long elapsed,
        String category,
        String prepared,
        String sql,
        String url
    ) {
        return formatSql(sql, elapsed, category);
    }

    /**
     * SQL 쿼리를 포매팅합니다.
     * - Hibernate의 FormatStyle을 사용하여 SQL 포매팅
     * - 바인딩된 인자가 이미 SQL에 포함됨
     *
     * @param sql SQL 쿼리
     * @param elapsed 실행 시간 (ms)
     * @param category 쿼리 카테고리
     * @return 포매팅된 SQL
     */
    private String formatSql(String sql, long elapsed, String category) {
        if (sql == null || sql.isBlank()) {
            return "";
        }

        // SQL 정규화: 과도한 공백 제거
        String cleanedSql = sql.trim()
            .replaceAll("\\s+", " ")
            .replaceAll("\\( ", "(")
            .replaceAll(" \\)", ")")
            .replaceAll(", ", ", ");

        // Hibernate FormatStyle을 사용하여 SQL 포매팅
        String formattedSql = formatWithHibernate(cleanedSql);

        return buildLogMessage(formattedSql, elapsed, category);
    }

    /**
     * Hibernate FormatStyle을 사용하여 SQL을 포매팅합니다.
     *
     * @param sql SQL 쿼리
     * @return 포매팅된 SQL
     */
    private String formatWithHibernate(String sql) {
        try {
            return FormatStyle.BASIC.getFormatter().format(sql);
        } catch (Exception e) {
            // 포매팅 실패 시 원본 반환
            return sql;
        }
    }

    /**
     * 최종 로그 메시지를 생성합니다.
     *
     * @param sql 포매팅된 SQL
     * @param elapsed 실행 시간 (ms)
     * @param category 카테고리
     * @return 포매팅된 로그 메시지
     */
    private String buildLogMessage(String sql, long elapsed, String category) {
        StringBuilder sb = new StringBuilder();

        sb.append("\n");
        sb.append("================== P6Spy SQL Logger ==================\n");
        sb.append("Category : ").append(category).append("\n");
        sb.append("Elapsed  : ").append(elapsed).append("ms\n");
        sb.append("SQL      :\n").append(sql).append("\n");
        sb.append("======================================================\n");

        return sb.toString();
    }
}
