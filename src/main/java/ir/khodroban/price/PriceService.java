package ir.khodroban.price;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PriceService {
    private static final int MINIMUM_SAMPLE = 3;
    private final JdbcTemplate jdbc;

    public PriceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public EstimateView analysis(UUID listingId) {
        Target target = jdbc.query("""
                SELECT id,normalized_make,normalized_model,normalized_trim,model_year,
                       mileage_km,region,asking_price_amount
                FROM listing WHERE id=?
                """, rs -> rs.next() ? target(rs) : null, listingId);
        if (target == null) throw notFound();
        ComparableResult result = comparables(new Criteria(target.make(), target.model(),
                target.trim(), target.year(), target.mileage(), target.region(), 100));
        List<Row> rows = result.items().stream()
                .filter(row -> !row.listingId().equals(listingId)).toList();
        if (retainedCount(rows) < MINIMUM_SAMPLE) {
            ComparableResult relaxed = comparables(new Criteria(target.make(), target.model(),
                    null, null, null, null, 100));
            List<Row> relaxedRows = relaxed.items().stream()
                    .filter(row -> !row.listingId().equals(listingId)).toList();
            if (retainedCount(relaxedRows) > retainedCount(rows)) {
                result = relaxed;
                rows = relaxedRows;
            }
        }
        PriceStatistics.Summary statistics = PriceStatistics.summarize(
                rows.stream().map(Row::amount).toList());
        List<Row> selectedRows = selectRetained(rows, statistics.retained());
        int sampleCount = selectedRows.size();
        String status = sampleCount < MINIMUM_SAMPLE ? "INSUFFICIENT_DATA" : "AVAILABLE";
        BigDecimal delta = null;
        BigDecimal deltaPercent = null;
        if (target.askingPrice() != null && statistics.median() != null) {
            delta = BigDecimal.valueOf(target.askingPrice()).subtract(statistics.median());
            deltaPercent = delta.multiply(BigDecimal.valueOf(100))
                    .divide(statistics.median(), 2, RoundingMode.HALF_UP);
        }
        UUID estimateId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO comparable_set(id,criteria_json,sample_count,sample_listing_ids,
                    selected_at,data_type)
                VALUES (?,?,?,?,CURRENT_TIMESTAMP,'ASKING')
                """, setId, criteriaJson(result.criteria()), sampleCount,
                ids(selectedRows));
        jdbc.update("""
                INSERT INTO price_estimate(id,comparable_set_id,listing_id,median_amount,
                    p25_amount,p75_amount,delta_amount,delta_percent,sample_count,
                    confidence,status,data_type,calculated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """, estimateId, setId, listingId, statistics.median(), statistics.p25(),
                statistics.p75(), delta, deltaPercent, sampleCount, confidence(sampleCount),
                status, "ASKING");
        return new EstimateView(estimateId, listingId, status, "ASKING_PRICE_ESTIMATE",
                statistics.median(), statistics.p25(), statistics.p75(), delta, deltaPercent,
                sampleCount, confidence(sampleCount), Instant.now(),
                "ASKING",
                status.equals("INSUFFICIENT_DATA")
                        ? "At least three comparable asking-price observations are required."
                        : "Asking prices are not verified transaction prices.");
    }

    @Transactional(readOnly = true)
    public ComparableResult comparables(Criteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT id,listing_title,normalized_make,normalized_model,normalized_trim,
                       model_year,mileage_km,region,asking_price_amount,asking_price_currency,
                       source_code,updated_at
                FROM listing WHERE asking_price_amount IS NOT NULL
                  AND LOWER(normalized_make)=LOWER(?)
                  AND LOWER(normalized_model)=LOWER(?)
                """);
        List<Object> params = new ArrayList<>(List.of(criteria.make(), criteria.model()));
        if (criteria.trim() != null && !criteria.trim().isBlank()) {
            sql.append(" AND LOWER(normalized_trim)=LOWER(?)");
            params.add(criteria.trim());
        }
        if (criteria.year() != null) {
            sql.append(" AND model_year BETWEEN ? AND ?");
            params.add(criteria.year() - 2);
            params.add(criteria.year() + 2);
        }
        if (criteria.mileageKm() != null) {
            sql.append(" AND mileage_km BETWEEN ? AND ?");
            params.add(Math.max(0, criteria.mileageKm() * 7 / 10));
            params.add(criteria.mileageKm() * 13 / 10);
        }
        if (criteria.region() != null && !criteria.region().isBlank()) {
            sql.append(" AND LOWER(region)=LOWER(?)");
            params.add(criteria.region());
        }
        sql.append(" ORDER BY updated_at DESC,id LIMIT ?");
        params.add(criteria.limit());
        List<Row> rows = jdbc.query(sql.toString(), (rs, row) -> new Row(
                rs.getObject("id", UUID.class), rs.getString("listing_title"),
                rs.getString("normalized_make"), rs.getString("normalized_model"),
                rs.getString("normalized_trim"), (Integer) rs.getObject("model_year"),
                number(rs.getBigDecimal("mileage_km")), rs.getString("region"),
                rs.getLong("asking_price_amount"), rs.getString("asking_price_currency"),
                rs.getString("source_code"), rs.getTimestamp("updated_at").toInstant()), params.toArray());
        return new ComparableResult(criteria, rows, "ASKING", Instant.now());
    }

    @Transactional(readOnly = true)
    public List<TrendPoint> history(String make, String model, String region) {
        String sql = """
                SELECT asking_price_amount,updated_at FROM listing
                WHERE asking_price_amount IS NOT NULL
                  AND LOWER(normalized_make)=LOWER(?) AND LOWER(normalized_model)=LOWER(?)
                """ + (region == null ? "" : " AND LOWER(region)=LOWER(?)");
        List<Object> params = new ArrayList<>(List.of(make, model));
        if (region != null) params.add(region);
        Map<LocalDate, List<Long>> grouped = new TreeMap<>();
        jdbc.query(sql, rs -> {
            grouped.computeIfAbsent(rs.getTimestamp("updated_at").toInstant()
                    .atZone(ZoneOffset.UTC).toLocalDate(), ignored -> new ArrayList<>())
                    .add(rs.getLong("asking_price_amount"));
        }, params.toArray());
        return grouped.entrySet().stream().map(entry -> {
            PriceStatistics.Summary summary = PriceStatistics.summarize(entry.getValue());
            return new TrendPoint(entry.getKey(), summary.median(), summary.retained().size(),
                    "ASKING");
        }).toList();
    }

    private static int retainedCount(List<Row> rows) {
        return PriceStatistics.summarize(rows.stream().map(Row::amount).toList())
                .retained().size();
    }

    private static List<Row> selectRetained(List<Row> rows, List<Long> retainedAmounts) {
        List<Long> remaining = new ArrayList<>(retainedAmounts);
        List<Row> selected = new ArrayList<>();
        for (Row row : rows) {
            if (remaining.remove(row.amount())) selected.add(row);
        }
        return selected;
    }

    private static Target target(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Target(rs.getObject("id", UUID.class), rs.getString("normalized_make"),
                rs.getString("normalized_model"), rs.getString("normalized_trim"),
                (Integer) rs.getObject("model_year"), number(rs.getBigDecimal("mileage_km")),
                rs.getString("region"), (Long) rs.getObject("asking_price_amount"));
    }

    private static Long number(java.math.BigDecimal value) {
        return value == null ? null : value.longValue();
    }

    private static double confidence(int samples) {
        if (samples < MINIMUM_SAMPLE) return 0.0;
        if (samples < 8) return 0.5;
        if (samples < 20) return 0.75;
        return 0.9;
    }

    private static String ids(List<Row> rows) {
        return rows.stream().map(row -> row.listingId().toString()).reduce("", (a, b) ->
                a.isEmpty() ? b : a + "," + b);
    }

    private static String criteriaJson(Criteria criteria) {
        return "{\"make\":\"" + escape(criteria.make()) + "\",\"model\":\""
                + escape(criteria.model()) + "\",\"trim\":\"" + escape(criteria.trim())
                + "\",\"year\":" + jsonNumber(criteria.year()) + ",\"mileageKm\":"
                + jsonNumber(criteria.mileageKm()) + ",\"region\":\""
                + escape(criteria.region()) + "\"}";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String jsonNumber(Number value) {
        return value == null ? "null" : value.toString();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    public record Criteria(String make, String model, String trim, Integer year,
                           Long mileageKm, String region, int limit) {}
    public record Row(UUID listingId, String title, String make, String model, String trim,
                      Integer year, Long mileageKm, String region, long amount, String currency,
                      String source, Instant observedAt) {}
    public record ComparableResult(Criteria criteria, List<Row> items, String dataType,
                                   Instant generatedAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record EstimateView(UUID id, UUID listingId, String status, String label,
                               BigDecimal median, BigDecimal p25, BigDecimal p75,
                               BigDecimal deltaAmount, BigDecimal deltaPercent, int sampleCount,
                               double confidence, Instant calculatedAt, String dataType,
                               String limitation) {}
    public record TrendPoint(LocalDate date, BigDecimal median, int sampleCount, String dataType) {}
    private record Target(UUID id, String make, String model, String trim, Integer year,
                          Long mileage, String region, Long askingPrice) {}
}
