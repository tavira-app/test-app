package ir.khodroban.listings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ListingService {
    private static final java.util.Map<String, String> SORTS = java.util.Map.of(
            "updatedAt", "l.updated_at",
            "createdAt", "l.created_at",
            "price", "l.asking_price_amount",
            "year", "l.model_year",
            "mileage", "l.mileage_km",
            "freshness", "ls.last_received_at");

    private final JdbcTemplate jdbc;

    public ListingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageView search(SearchRequest request) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        add(where, params, "l.normalized_make", request.make());
        add(where, params, "l.normalized_model", request.model());
        add(where, params, "l.normalized_trim", request.trim());
        range(where, params, "l.model_year", request.yearFrom(), request.yearTo());
        range(where, params, "l.mileage_km", request.mileageFrom(), request.mileageTo());
        range(where, params, "l.asking_price_amount", request.priceFrom(), request.priceTo());
        add(where, params, "l.region", request.region());
        add(where, params, "l.fuel_type", request.fuel());
        add(where, params, "l.transmission", request.transmission());
        add(where, params, "l.body_type", request.body());

        String from = """
                FROM listing l
                LEFT JOIN listing_source ls ON ls.listing_id=l.id
                """;
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class,
                params.toArray());
        String sort = SORTS.get(request.sort());
        if (sort == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort field.");
        }
        String direction = switch (request.direction().toLowerCase(java.util.Locale.ROOT)) {
            case "asc" -> " ASC";
            case "desc" -> " DESC";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sort direction must be asc or desc.");
        };
        int offset = Math.multiplyExact(request.page(), request.size());
        List<Object> rowParams = new ArrayList<>(params);
        rowParams.add(request.size());
        rowParams.add(offset);
        List<ListView> rows = jdbc.query("""
                SELECT l.id,l.listing_title,l.normalized_make,l.normalized_model,l.normalized_trim,
                       l.model_year,l.mileage_km,l.asking_price_amount,l.asking_price_currency,
                       l.city,l.region,l.fuel_type,l.transmission,l.body_type,
                       l.updated_at,l.source_code,ls.external_id,ls.last_received_at
                """ + from + where + " ORDER BY " + sort + direction + ", l.id LIMIT ? OFFSET ?",
                (rs, row) -> listView(rs), rowParams.toArray());
        int pages = total == 0 ? 0 : (int) ((total + request.size() - 1) / request.size());
        return new PageView(rows, request.page(), request.size(), total, pages);
    }

    @Transactional(readOnly = true)
    public DetailView detail(UUID id) {
        List<DetailView> rows = jdbc.query("""
                SELECT l.id,l.listing_title,l.normalized_make,l.normalized_model,l.normalized_trim,
                       l.model_year,l.mileage_km,l.asking_price_amount,l.asking_price_currency,
                       l.city,l.region,l.fuel_type,l.transmission,l.body_type,l.normalized_data,
                       l.created_at,l.updated_at,l.source_code,ls.external_id,ls.last_received_at
                FROM listing l LEFT JOIN listing_source ls ON ls.listing_id=l.id WHERE l.id=?
                """, (rs, row) -> detailView(rs), id);
        if (rows.isEmpty()) throw notFound();
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public HistoryView history(UUID id) {
        requireListing(id);
        List<SnapshotView> snapshots = jdbc.query("""
                SELECT id,normalized_data,source_code,observed_at
                FROM listing_snapshot WHERE listing_id=? ORDER BY observed_at DESC,id DESC
                """, (rs, row) -> new SnapshotView(rs.getObject("id", UUID.class),
                rs.getString("normalized_data"), rs.getString("source_code"),
                rs.getTimestamp("observed_at").toInstant()), id);
        List<PriceView> prices = jdbc.query("""
                SELECT id,price_amount,price_currency,price_type,source_code,observed_at
                FROM price_observation WHERE listing_id=? ORDER BY observed_at DESC,id DESC
                """, (rs, row) -> new PriceView(rs.getObject("id", UUID.class),
                rs.getLong("price_amount"), rs.getString("price_currency"),
                rs.getString("price_type"), rs.getString("source_code"),
                rs.getTimestamp("observed_at").toInstant()), id);
        return new HistoryView(snapshots, prices);
    }

    @Transactional(readOnly = true)
    public List<MediaView> media(UUID id) {
        requireListing(id);
        return jdbc.query("""
                SELECT id,source_code,media_url,media_type,provenance_source,confidence,created_at
                FROM listing_media WHERE listing_id=? ORDER BY created_at,id
                """, (rs, row) -> new MediaView(rs.getObject("id", UUID.class),
                rs.getString("source_code"), rs.getString("media_url"), rs.getString("media_type"),
                rs.getString("provenance_source"), rs.getBigDecimal("confidence"),
                rs.getTimestamp("created_at").toInstant()), id);
    }

    private void add(StringBuilder where, List<Object> params, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND LOWER(").append(column).append(")=LOWER(?)");
            params.add(value.trim());
        }
    }

    private void range(StringBuilder where, List<Object> params, String column, Number from, Number to) {
        if (from != null) {
            where.append(" AND ").append(column).append(">=?");
            params.add(from);
        }
        if (to != null) {
            where.append(" AND ").append(column).append("<=?");
            params.add(to);
        }
    }

    private ListView listView(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ListView(rs.getObject("id", UUID.class), rs.getString("listing_title"),
                rs.getString("normalized_make"), rs.getString("normalized_model"),
                rs.getString("normalized_trim"), (Integer) rs.getObject("model_year"),
                rs.getBigDecimal("mileage_km"), rs.getObject("asking_price_amount", Long.class),
                rs.getString("asking_price_currency"), rs.getString("city"), rs.getString("region"),
                rs.getString("fuel_type"), rs.getString("transmission"), rs.getString("body_type"),
                rs.getTimestamp("updated_at").toInstant(), rs.getString("source_code"),
                rs.getString("external_id"), instant(rs.getTimestamp("last_received_at")));
    }

    private DetailView detailView(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DetailView(listView(rs), rs.getString("normalized_data"),
                rs.getTimestamp("created_at").toInstant());
    }

    private void requireListing(UUID id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM listing WHERE id=?", Integer.class, id);
        if (count == null || count == 0) throw notFound();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static Instant instant(java.sql.Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record SearchRequest(String make, String model, String trim, Integer yearFrom, Integer yearTo,
                                Long mileageFrom, Long mileageTo, Long priceFrom, Long priceTo,
                                String region, String fuel, String transmission, String body,
                                int page, int size, String sort, String direction) {}
    public record PageView(List<ListView> content, int page, int size, long totalElements, int totalPages) {}
    public record ListView(UUID id, String title, String make, String model, String trim, Integer year,
                           java.math.BigDecimal mileageKm, Long priceAmount, String priceCurrency,
                           String city, String region, String fuel, String transmission, String body,
                           Instant updatedAt, String source, String externalId, Instant lastReceivedAt) {}
    public record DetailView(ListView listing, String normalizedData, Instant createdAt) {}
    public record HistoryView(List<SnapshotView> snapshots, List<PriceView> prices) {}
    public record SnapshotView(UUID id, String normalizedData, String source, Instant observedAt) {}
    public record PriceView(UUID id, Long amount, String currency, String type, String source,
                            Instant observedAt) {}
    public record MediaView(UUID id, String source, String url, String type, String provenance,
                            java.math.BigDecimal confidence, Instant createdAt) {}
}
