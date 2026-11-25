package ir.khodroban.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SourceService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SourceService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<SourceView> sources() {
        return jdbc.query("""
                SELECT code,display_name,source_type,enabled,configured,access_authorized,
                       health_status,health_detail,adapter_status,checked_at
                FROM source_provider ORDER BY code
                """, (rs, row) -> new SourceView(rs.getString("code"), rs.getString("display_name"),
                rs.getString("source_type"), rs.getBoolean("enabled"), rs.getBoolean("configured"),
                rs.getBoolean("access_authorized"), rs.getString("health_status"),
                rs.getString("health_detail"), rs.getString("adapter_status"),
                instant(rs.getTimestamp("checked_at"))));
    }

    @Transactional
    public SourceView configure(String code, SourceController.Configuration request) {
        Provider provider = provider(code);
        boolean hasReference = request.credentialReference() != null
                && !request.credentialReference().isBlank();
        if (request.enabled() && (!request.accessAuthorized() || !hasReference)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Enabling a source requires authorization and a secret reference.");
        }
        jdbc.update("""
                UPDATE source_provider SET enabled=?,configured=?,access_authorized=?,
                    credential_reference=?,health_status=?,health_detail=?,checked_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, request.enabled(), hasReference, request.accessAuthorized(),
                hasReference ? request.credentialReference().trim() : null,
                provider.sourceType().equals("SYNTHETIC") ? "HEALTHY" : "NOT_IMPLEMENTED",
                provider.sourceType().equals("SYNTHETIC") ? "Explicit synthetic fixture source."
                        : "No external adapter is installed; no live requests are made.", provider.id());
        return source(code);
    }

    @Transactional
    public RunView ingest(String code, String key, SourceController.IngestionRequest request) {
        Map<String, Object> normalizedData = Map.of("sourceCode", code);

        Provider provider = provider(code);
        boolean fixture = request.mode() == SourceController.Mode.SYNTHETIC_FIXTURE
                && provider.sourceType().equals("SYNTHETIC") && code.equals("SYNTHETIC_FIXTURE");
        boolean authorizedImport = request.mode() == SourceController.Mode.AUTHORIZED_IMPORT
                && provider.enabled() && provider.configured() && provider.authorized();
        if (!fixture && !authorizedImport) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only explicitly labeled fixtures or enabled, authorized imports are accepted.");
        }
        String requestHash = hash(json(request));
        jdbc.update("""
                INSERT INTO data_ingestion_run(id,provider_id,idempotency_key,request_hash,status)
                VALUES (?,?,?,?,'RUNNING')
                ON CONFLICT (provider_id,idempotency_key) DO NOTHING
                """, UUID.randomUUID(), provider.id(), key, requestHash);
        Run run = jdbc.query("""
                SELECT id,request_hash,status,created_count,updated_count,unchanged_count
                FROM data_ingestion_run WHERE provider_id=? AND idempotency_key=?
                """, rs -> rs.next() ? new Run(rs.getObject("id", UUID.class),
                rs.getString("request_hash"), rs.getString("status"), rs.getInt("created_count"),
                rs.getInt("updated_count"), rs.getInt("unchanged_count")) : null, provider.id(), key);
        if (run == null) throw new IllegalStateException("Ingestion run was not persisted.");
        if (!run.requestHash().equals(requestHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency-Key was already used with a different request.");
        }
        if (!run.status().equals("RUNNING")) {
            return new RunView(run.id(), code, run.status(), run.created(), run.updated(),
                    run.unchanged(), true, Instant.now());
        }

        int created = 0, updated = 0, unchanged = 0;
        for (SourceController.RecordInput record : request.records()) {
            if (record.rawPayload() == null || !record.rawPayload().isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each raw payload must be a JSON object.");
            }
            String payload = json(record.rawPayload());
            String payloadHash = hash(payload);
            Map<String, Object> recordData = new java.util.LinkedHashMap<>(normalizedData);
            recordData.put("rawFields", record.rawPayload());
            recordData.put("receivedAt", Instant.now().toString());
            String normalized = json(recordData);
            String normalizedHash = hash(normalized);
            String previous = jdbc.query("""
                    SELECT payload_hash FROM raw_listing
                    WHERE provider_id=? AND external_id=?
                    """, rs -> rs.next() ? rs.getString(1) : null, provider.id(), record.externalId());

            String listingHash = hash(code + "|" + record.externalId());
            String existingListing = jdbc.query("""
                    SELECT id FROM listing WHERE listing_hash=? AND source_code=?
                    """, rs -> rs.next() ? rs.getString(1) : null, listingHash, code);

            UUID listingId;
            if (existingListing == null) {
                listingId = UUID.randomUUID();
                jdbc.update("""
                    INSERT INTO listing(id,source_code,external_id,listing_hash,normalized_data,normalized_hash,payload_hash,created_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, listingId, code, record.externalId(), listingHash, normalized, normalizedHash, payloadHash, Timestamp.from(Instant.now()));
            } else {
                listingId = UUID.fromString(existingListing);
                jdbc.update("""
                    UPDATE listing SET listing_hash=?,payload_hash=?,normalized_hash=?,normalized_data=?,updated_at=?
                    WHERE id=?
                    """, listingHash, payloadHash, normalizedHash, normalized, Timestamp.from(Instant.now()), listingId);
            }

            jdbc.update("""
                    INSERT INTO listing_source(listing_id,provider_id,external_id)
                    VALUES (?,?,?) ON CONFLICT (provider_id,external_id) DO UPDATE
                    SET listing_id=EXCLUDED.listing_id,last_received_at=CURRENT_TIMESTAMP
                    """, listingId, provider.id(), record.externalId());
            jdbc.update("""
                    INSERT INTO listing_snapshot(id,listing_id,normalized_data,normalized_hash,source_code,observed_at)
                    VALUES (?,?,?,?,?,?) ON CONFLICT (listing_id,normalized_hash) DO NOTHING
                    """, UUID.randomUUID(), listingId, normalized, normalizedHash, code, Timestamp.from(Instant.now()));
            UUID snapshotId = jdbc.query("""
                    SELECT id FROM listing_snapshot WHERE listing_id=? AND normalized_hash=?
                    """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, listingId, normalizedHash);
            jdbc.update("""
                    INSERT INTO field_provenance(id,listing_id,snapshot_id,field_name,raw_value,
                        normalized_value,confidence,source)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), listingId, snapshotId, "rawFields", payload, normalized,
                    new java.math.BigDecimal("0.500"), "SOURCE:" + code);

            String outcome = previous == null ? "CREATED"
                    : previous.equals(payloadHash) ? "UNCHANGED" : "UPDATED";
            jdbc.update("""
                    INSERT INTO raw_listing(id,provider_id,external_id,payload,payload_hash,last_run_id)
                    VALUES (?,?,?,?,?,?)
                    ON CONFLICT (provider_id,external_id) DO UPDATE SET payload=EXCLUDED.payload,
                        payload_hash=EXCLUDED.payload_hash,last_run_id=EXCLUDED.last_run_id,
                        last_received_at=CURRENT_TIMESTAMP
                    """, UUID.randomUUID(), provider.id(), record.externalId(), payload, payloadHash, run.id());
            jdbc.update("""
                    INSERT INTO raw_listing_revision(id,provider_id,external_id,payload,payload_hash,run_id)
                    VALUES (?,?,?,?,?,?) ON CONFLICT (provider_id,external_id,payload_hash) DO NOTHING
                    """, UUID.randomUUID(), provider.id(), record.externalId(), payload, payloadHash, run.id());
            jdbc.update("""
                    INSERT INTO data_ingestion_record(run_id,provider_id,external_id,payload_hash,outcome)
                    VALUES (?,?,?,?,?) ON CONFLICT (run_id,external_id) DO NOTHING
                    """, run.id(), provider.id(), record.externalId(), payloadHash, outcome);
            switch (outcome) {
                case "CREATED" -> created++;
                case "UPDATED" -> updated++;
                default -> unchanged++;
            }
        }
        jdbc.update("""
                UPDATE data_ingestion_run SET status='SUCCEEDED',created_count=?,
                    updated_count=?,unchanged_count=?,finished_at=CURRENT_TIMESTAMP WHERE id=?
                """, created, updated, unchanged, run.id());
        return new RunView(run.id(), code, "SUCCEEDED", created, updated, unchanged, false, Instant.now());
    }

    private SourceView source(String code) {
        return sources().stream().filter(item -> item.code().equals(code)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Provider provider(String code) {
        List<Provider> rows = jdbc.query("""
                SELECT id,source_type,enabled,configured,access_authorized FROM source_provider WHERE code=?
                """, (rs, row) -> new Provider(rs.getObject("id", UUID.class), rs.getString("source_type"),
                rs.getBoolean("enabled"), rs.getBoolean("configured"), rs.getBoolean("access_authorized")), code);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return rows.getFirst();
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Request could not be serialized.", exception);
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record SourceView(String code, String displayName, String type, boolean enabled,
                             boolean configured, boolean accessAuthorized, String health,
                             String healthDetail, String adapterStatus, Instant checkedAt) {}
    public record RunView(UUID id, String sourceCode, String status, int created,
                          int updated, int unchanged, boolean replayed, Instant completedAt) {}
    private record Provider(UUID id, String sourceType, boolean enabled, boolean configured,
                            boolean authorized) {}
    private record Run(UUID id, String requestHash, String status, int created,
                       int updated, int unchanged) {}
}
