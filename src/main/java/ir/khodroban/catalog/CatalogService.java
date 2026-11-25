package ir.khodroban.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatalogService {
    private final JdbcTemplate jdbc;

    public CatalogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<MakeView> makes(String query) {
        String pattern = pattern(query);
        return jdbc.query("""
                SELECT id,name,country_code FROM vehicle_make
                WHERE CAST(? AS VARCHAR) IS NULL OR name_normalized LIKE CAST(? AS VARCHAR)
                ORDER BY name
                """, (rs, row) -> new MakeView(rs.getObject("id", UUID.class),
                rs.getString("name"), rs.getString("country_code")), pattern, pattern);
    }

    @Transactional
    public MakeView createMake(String name, String countryCode) {
        UUID id = UUID.randomUUID();
        String cleaned = clean(name);
        String country = countryCode == null || countryCode.isBlank() ? null : countryCode.trim();
        jdbc.update("INSERT INTO vehicle_make(id,name,name_normalized,country_code) VALUES (?,?,?,?)",
                id, cleaned, normalize(cleaned), country);
        return new MakeView(id, cleaned, country);
    }

    @Transactional(readOnly = true)
    public List<ModelView> models(UUID makeId, String query) {
        String pattern = pattern(query);
        return jdbc.query("""
                SELECT id,make_id,name FROM vehicle_model
                WHERE (CAST(? AS UUID) IS NULL OR make_id=CAST(? AS UUID))
                  AND (CAST(? AS VARCHAR) IS NULL OR name_normalized LIKE CAST(? AS VARCHAR))
                ORDER BY name
                """, (rs, row) -> new ModelView(rs.getObject("id", UUID.class),
                rs.getObject("make_id", UUID.class), rs.getString("name")),
                makeId, makeId, pattern, pattern);
    }

    @Transactional
    public ModelView createModel(UUID makeId, String name) {
        requireExists("vehicle_make", makeId);
        UUID id = UUID.randomUUID();
        String cleaned = clean(name);
        jdbc.update("INSERT INTO vehicle_model(id,make_id,name,name_normalized) VALUES (?,?,?,?)",
                id, makeId, cleaned, normalize(cleaned));
        return new ModelView(id, makeId, cleaned);
    }

    @Transactional(readOnly = true)
    public List<GenerationView> generations(UUID modelId) {
        requireExists("vehicle_model", modelId);
        return jdbc.query("SELECT id,model_id,name FROM vehicle_generation WHERE model_id=? ORDER BY name",
                (rs, row) -> new GenerationView(rs.getObject("id", UUID.class),
                        rs.getObject("model_id", UUID.class), rs.getString("name")), modelId);
    }

    @Transactional
    public GenerationView createGeneration(UUID modelId, String name) {
        requireExists("vehicle_model", modelId);
        UUID id = UUID.randomUUID();
        String cleaned = clean(name);
        jdbc.update("INSERT INTO vehicle_generation(id,model_id,name,name_normalized) VALUES (?,?,?,?)",
                id, modelId, cleaned, normalize(cleaned));
        return new GenerationView(id, modelId, cleaned);
    }

    @Transactional(readOnly = true)
    public List<TrimView> trims(UUID modelId, String query) {
        String pattern = pattern(query);
        return jdbc.query("""
                SELECT id,model_id,generation_id,name FROM vehicle_trim
                WHERE (CAST(? AS UUID) IS NULL OR model_id=CAST(? AS UUID))
                  AND (CAST(? AS VARCHAR) IS NULL OR name_normalized LIKE CAST(? AS VARCHAR))
                ORDER BY name
                """, (rs, row) -> new TrimView(rs.getObject("id", UUID.class),
                rs.getObject("model_id", UUID.class), rs.getObject("generation_id", UUID.class),
                rs.getString("name")), modelId, modelId, pattern, pattern);
    }

    @Transactional
    public TrimView createTrim(UUID modelId, UUID generationId, String name) {
        requireExists("vehicle_model", modelId);
        if (generationId != null) {
            UUID parent = jdbc.query("SELECT model_id FROM vehicle_generation WHERE id=?",
                    rs -> rs.next() ? rs.getObject(1, UUID.class) : null, generationId);
            if (!modelId.equals(parent)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        UUID id = UUID.randomUUID();
        String cleaned = clean(name);
        jdbc.update("INSERT INTO vehicle_trim(id,model_id,generation_id,name,name_normalized) VALUES (?,?,?,?,?)",
                id, modelId, generationId, cleaned, normalize(cleaned));
        return new TrimView(id, modelId, generationId, cleaned);
    }

    @Transactional
    public AliasView addAlias(UUID trimId, String alias) {
        requireExists("vehicle_trim", trimId);
        UUID id = UUID.randomUUID();
        String cleaned = clean(alias);
        jdbc.update("INSERT INTO vehicle_trim_alias(id,trim_id,alias,alias_normalized) VALUES (?,?,?,?)",
                id, trimId, cleaned, normalize(cleaned));
        return new AliasView(id, trimId, cleaned);
    }

    @Transactional(readOnly = true)
    public SpecificationView specification(UUID trimId) {
        requireExists("vehicle_trim", trimId);
        List<SpecificationView> rows = jdbc.query("""
                SELECT trim_id,engine_cc,power_kw,fuel_type,transmission,consumption_litres_per_100_km,
                       provenance_source,confidence,updated_at
                FROM vehicle_specification WHERE trim_id=?
                """, (rs, row) -> new SpecificationView(rs.getObject("trim_id", UUID.class),
                (Integer) rs.getObject("engine_cc"), rs.getBigDecimal("power_kw"), rs.getString("fuel_type"),
                rs.getString("transmission"), rs.getBigDecimal("consumption_litres_per_100_km"),
                rs.getString("provenance_source"), rs.getBigDecimal("confidence"),
                rs.getTimestamp("updated_at").toInstant()), trimId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return rows.getFirst();
    }

    @Transactional
    public SpecificationView saveSpecification(UUID trimId, CatalogController.SpecificationRequest request) {
        requireExists("vehicle_trim", trimId);
        jdbc.update("""
                INSERT INTO vehicle_specification(trim_id,engine_cc,power_kw,fuel_type,transmission,
                    consumption_litres_per_100_km,provenance_source,confidence)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT (trim_id) DO UPDATE SET engine_cc=excluded.engine_cc,power_kw=excluded.power_kw,
                    fuel_type=excluded.fuel_type,transmission=excluded.transmission,
                    consumption_litres_per_100_km=excluded.consumption_litres_per_100_km,
                    provenance_source=excluded.provenance_source,confidence=excluded.confidence,
                    updated_at=CURRENT_TIMESTAMP
                """, trimId, request.engineCc(), request.powerKw(), blankToNull(request.fuelType()),
                blankToNull(request.transmission()), request.consumptionLitresPer100Km(),
                clean(request.provenanceSource()), request.confidence());
        return specification(trimId);
    }

    @Transactional(readOnly = true)
    public List<SearchResult> search(String query) {
        String like = pattern(query);
        return jdbc.query("""
                SELECT ma.id make_id,ma.name make_name,mo.id model_id,mo.name model_name,
                       t.id trim_id,t.name trim_name
                FROM vehicle_make ma JOIN vehicle_model mo ON mo.make_id=ma.id
                LEFT JOIN vehicle_trim t ON t.model_id=mo.id
                WHERE ma.name_normalized LIKE ? OR mo.name_normalized LIKE ?
                   OR COALESCE(t.name_normalized,'') LIKE ?
                   OR EXISTS (SELECT 1 FROM vehicle_trim_alias a
                              WHERE a.trim_id=t.id AND a.alias_normalized LIKE ?)
                ORDER BY ma.name,mo.name,t.name LIMIT 50
                """, (rs, row) -> new SearchResult(rs.getObject("make_id", UUID.class), rs.getString("make_name"),
                rs.getObject("model_id", UUID.class), rs.getString("model_name"),
                rs.getObject("trim_id", UUID.class), rs.getString("trim_name")), like, like, like, like);
    }

    @Transactional(readOnly = true)
    public Resolution resolve(String text) {
        String value = normalize(text);
        List<ResolutionCandidate> matches = jdbc.query("""
                SELECT t.id trim_id,ma.id make_id,ma.name make_name,mo.id model_id,mo.name model_name,
                       t.name trim_name,'CANONICAL_NAME' source
                FROM vehicle_trim t JOIN vehicle_model mo ON mo.id=t.model_id
                    JOIN vehicle_make ma ON ma.id=mo.make_id
                WHERE t.name_normalized=?
                UNION ALL
                SELECT t.id,ma.id,ma.name,mo.id,mo.name,t.name,'CATALOG_ALIAS'
                FROM vehicle_trim_alias a JOIN vehicle_trim t ON t.id=a.trim_id
                    JOIN vehicle_model mo ON mo.id=t.model_id JOIN vehicle_make ma ON ma.id=mo.make_id
                WHERE a.alias_normalized=?
                ORDER BY trim_id,source
                """, (rs, row) -> new ResolutionCandidate(rs.getObject("trim_id", UUID.class),
                rs.getObject("make_id", UUID.class), rs.getString("make_name"),
                rs.getObject("model_id", UUID.class), rs.getString("model_name"),
                rs.getString("trim_name"), rs.getString("source")), value, value)
                .stream().collect(java.util.stream.Collectors.toMap(ResolutionCandidate::trimId,
                        candidate -> candidate, (first, ignored) -> first, java.util.LinkedHashMap::new))
                .values().stream().toList();
        if (matches.size() != 1) {
            return new Resolution("UNKNOWN", null, null, null, null, null, 0.0,
                    "NO_CONFIDENT_MATCH", matches.isEmpty() ? "NO_MATCH" : "AMBIGUOUS");
        }
        ResolutionCandidate match = matches.getFirst();
        return new Resolution("RESOLVED", match.makeId(), match.makeName(), match.modelId(),
                match.modelName(), match.trimId(), 1.0, match.source(), match.trimName());
    }

    private void requireExists(String table, UUID id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id=?",
                Integer.class, id);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static String normalize(String value) {
        return clean(value).toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String clean(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String pattern(String query) {
        return query == null || query.isBlank() ? null : "%" + normalize(query) + "%";
    }

    public record MakeView(UUID id, String name, String countryCode) {}
    public record ModelView(UUID id, UUID makeId, String name) {}
    public record GenerationView(UUID id, UUID modelId, String name) {}
    public record TrimView(UUID id, UUID modelId, UUID generationId, String name) {}
    public record AliasView(UUID id, UUID trimId, String alias) {}
    public record SpecificationView(UUID trimId, Integer engineCc, BigDecimal powerKw, String fuelType,
                                    String transmission, BigDecimal consumptionLitresPer100Km,
                                    String provenanceSource, BigDecimal confidence, java.time.Instant updatedAt) {}
    public record SearchResult(UUID makeId, String make, UUID modelId, String model, UUID trimId, String trim) {}
    public record Resolution(String status, UUID makeId, String make, UUID modelId, String model,
                             UUID trimId, double confidence, String provenance, String detail) {}
    private record ResolutionCandidate(UUID trimId, UUID makeId, String makeName, UUID modelId,
                                      String modelName, String trimName, String source) {}
}
