package ir.khodroban.alerts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.khodroban.alerts.AlertController.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlertService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AlertService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public FavoriteView favorite(UUID listingId) {
        Actor actor = actor();
        UUID workspace = workspace(actor.userId());
        requireListing(listingId);
        jdbc.update("""
                INSERT INTO favorite(user_id,workspace_id,listing_id)
                VALUES (?,?,?) ON CONFLICT (user_id,listing_id) DO NOTHING
                """, actor.userId(), workspace, listingId);
        return jdbc.queryForObject("""
                SELECT created_at FROM favorite WHERE user_id=? AND listing_id=?
                """, (rs, row) -> new FavoriteView(listingId, rs.getTimestamp(1).toInstant()),
                actor.userId(), listingId);
    }

    @Transactional
    public void unfavorite(UUID listingId) {
        jdbc.update("DELETE FROM favorite WHERE user_id=? AND listing_id=?", actor().userId(), listingId);
    }

    @Transactional(readOnly = true)
    public List<FavoriteView> favorites() {
        return jdbc.query("""
                SELECT listing_id,created_at FROM favorite WHERE user_id=?
                ORDER BY created_at DESC
                """, (rs, row) -> new FavoriteView(rs.getObject(1, UUID.class),
                rs.getTimestamp(2).toInstant()), actor().userId());
    }

    @Transactional
    public SavedSearchView saveSearch(SavedSearchRequest request) {
        Actor actor = actor();
        UUID workspace = workspace(actor.userId());
        parseCriteria(request.criteria());
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO saved_search(id,user_id,workspace_id,name,criteria,new_listing,price_change)
                VALUES (?,?,?,?,?,?,?)
                """, id, actor.userId(), workspace, request.name().trim(), request.criteria(),
                request.newListing(), request.priceChange());
        return new SavedSearchView(id, request.name().trim(), request.criteria(), true,
                request.newListing(), request.priceChange(), Instant.now());
    }

    @Transactional(readOnly = true)
    public List<SavedSearchView> searches() {
        return jdbc.query("""
                SELECT id,name,criteria,active,new_listing,price_change,created_at
                FROM saved_search WHERE user_id=? ORDER BY created_at DESC
                """, (rs, row) -> new SavedSearchView(rs.getObject(1, UUID.class),
                rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getBoolean(5),
                rs.getBoolean(6), rs.getTimestamp(7).toInstant()), actor().userId());
    }

    @Transactional
    public void deleteSearch(UUID id) {
        int changed = jdbc.update("DELETE FROM saved_search WHERE id=? AND user_id=?", id, actor().userId());
        if (changed == 0) throw notFound();
    }

    @Transactional
    public PreferenceView preference(PreferenceRequest request) {
        Actor actor = actor();
        UUID workspace = workspace(actor.userId());
        jdbc.update("""
                INSERT INTO alert_preference(user_id,workspace_id,consent,channel,daily_quota)
                VALUES (?,?,?,?,?) ON CONFLICT (user_id,workspace_id) DO UPDATE SET
                    consent=EXCLUDED.consent,channel=EXCLUDED.channel,
                    daily_quota=EXCLUDED.daily_quota,updated_at=CURRENT_TIMESTAMP
                """, actor.userId(), workspace, request.consent(), request.channel().name(),
                request.dailyQuota());
        return requestValue(actor.userId(), workspace);
    }

    @Transactional(readOnly = true)
    public PreferenceView preference() {
        Actor actor = actor();
        return requestValue(actor.userId(), workspace(actor.userId()));
    }

    @Transactional(readOnly = true)
    public List<OutboxView> alerts(int limit) {
        return jdbc.query("""
                SELECT id,saved_search_id,listing_id,event_type,status,attempts,created_at
                FROM notification_outbox WHERE user_id=? ORDER BY created_at DESC LIMIT ?
                """, (rs, row) -> new OutboxView(rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getString(4),
                rs.getString(5), rs.getInt(6), rs.getTimestamp(7).toInstant()),
                actor().userId(), limit);
    }

    @Transactional
    public DeliveryJobView deliverInApp() {
        UUID job = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO job_record(id,job_name,status,started_at)
                VALUES (?,?,?,CURRENT_TIMESTAMP)
                """, job, "in-app-alert-delivery", "RUNNING");
        int delivered = jdbc.update("""
                UPDATE notification_outbox
                SET status='SENT', attempts=attempts + 1, sent_at=CURRENT_TIMESTAMP
                WHERE status='PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
                """);
        jdbc.update("""
                UPDATE job_record
                SET status='SUCCEEDED',generated_count=?,completed_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, delivered, job);
        return new DeliveryJobView(job, "SUCCEEDED", delivered, Instant.now());
    }

    @Transactional
    public JobView generateAlerts() {
        UUID job = UUID.randomUUID();
        Instant cursor = jdbc.query("""
                SELECT cursor_at FROM alert_job_cursor WHERE job_name='listing-alerts' FOR UPDATE
                """, rs -> rs.next() ? rs.getTimestamp(1).toInstant() : Instant.EPOCH);
        jdbc.update("""
                INSERT INTO job_record(id,job_name,status,started_at)
                VALUES (?,?,?,CURRENT_TIMESTAMP)
                """, job, "listing-alerts", "RUNNING");
        List<Listing> listings = jdbc.query("""
                SELECT id,created_at,updated_at FROM listing
                WHERE created_at>=? OR updated_at>=? ORDER BY updated_at,id
                """, (rs, row) -> new Listing(rs.getObject(1, UUID.class),
                rs.getTimestamp(2).toInstant(), rs.getTimestamp(3).toInstant()), Timestamp.from(cursor),
                Timestamp.from(cursor));
        List<Search> searches = jdbc.query("""
                SELECT id,user_id,criteria,new_listing,price_change FROM saved_search WHERE active=true
                """, (rs, row) -> new Search(rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class), rs.getString(3), rs.getBoolean(4), rs.getBoolean(5)));
        int generated = 0;
        Instant next = cursor;
        for (Listing listing : listings) {
            next = next.isAfter(listing.updatedAt()) ? next : listing.updatedAt();
            for (Search search : searches) {
                if (!matches(search.criteria(), listing.id())) continue;
                Preference pref = preference(search.userId());
                if (pref == null || !pref.consent() || pref.quota() <= sentToday(search.userId())) continue;
                if (search.newListing() && listing.createdAt().isAfter(cursor))
                    generated += enqueue(search, listing, "NEW_LISTING", listing.createdAt().toString());
                if (search.priceChange() && listing.updatedAt().isAfter(cursor)
                        && listing.updatedAt().isAfter(listing.createdAt()))
                    generated += enqueue(search, listing, "PRICE_CHANGE", listing.updatedAt().toString());
            }
        }
        jdbc.update("""
                INSERT INTO alert_job_cursor(job_name,cursor_at) VALUES ('listing-alerts',?)
                ON CONFLICT (job_name) DO UPDATE SET cursor_at=EXCLUDED.cursor_at
                """, Timestamp.from(next));
        jdbc.update("UPDATE job_record SET status='SUCCEEDED',generated_count=?,completed_at=CURRENT_TIMESTAMP WHERE id=?",
                generated, job);
        return new JobView(job, "SUCCEEDED", generated, Instant.now());
    }

    private int enqueue(Search search, Listing listing, String type, String key) {
        UUID eventId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        return jdbc.update("""
                WITH inserted_event AS (
                    INSERT INTO alert_event(id,saved_search_id,user_id,listing_id,event_type,event_key)
                    VALUES (?,?,?,?,?,?)
                    ON CONFLICT (saved_search_id,listing_id,event_type,event_key) DO NOTHING
                    RETURNING id,user_id,saved_search_id,listing_id,event_type
                )
                INSERT INTO notification_outbox(
                    id,event_id,user_id,saved_search_id,listing_id,event_type,status)
                SELECT ?,id,user_id,saved_search_id,listing_id,event_type,'PENDING'
                FROM inserted_event
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, search.id(), search.userId(), listing.id(), type, key, outboxId);
    }

    private boolean matches(String criteria, UUID listingId) {
        JsonNode node = parseCriteria(criteria);
        Map<String, String> fields = jdbc.query("""
                SELECT normalized_make,normalized_model,region,fuel_type,transmission,body_type
                FROM listing WHERE id=?
                """, rs -> {
            if (!rs.next()) return Map.<String, String>of();
            Map<String, String> values = new HashMap<>();
            values.put("make", rs.getString(1)); values.put("model", rs.getString(2));
            values.put("region", rs.getString(3)); values.put("fuel", rs.getString(4));
            values.put("transmission", rs.getString(5)); values.put("body", rs.getString(6));
            return values;
        }, listingId);
        for (String key : List.of("make", "model", "region", "fuel", "transmission", "body")) {
            if (node.hasNonNull(key) && !node.get(key).asText().equalsIgnoreCase(fields.get(key))) return false;
        }
        return true;
    }

    private PreferenceView requestValue(UUID user, UUID workspace) {
        Preference value = preference(user);
        return value == null ? new PreferenceView(false, Channel.IN_APP, 0)
                : new PreferenceView(value.consent(), Channel.valueOf(value.channel()), value.quota());
    }

    private Preference preference(UUID user) {
        return jdbc.query("""
                SELECT consent,channel,daily_quota FROM alert_preference WHERE user_id=?
                """, rs -> rs.next() ? new Preference(rs.getBoolean(1), rs.getString(2), rs.getInt(3)) : null, user);
    }

    private int sentToday(UUID user) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification_outbox
                WHERE user_id=? AND created_at>=? AND status IN ('PENDING','SENT')
                """, Integer.class, user, Timestamp.from(LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC)));
    }

    private UUID workspace(UUID user) {
        return jdbc.queryForObject("""
                SELECT workspace_id FROM workspace_member
                WHERE user_id=? ORDER BY joined_at,workspace_id LIMIT 1
                """,
                UUID.class, user);
    }

    private void requireListing(UUID id) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM listing WHERE id=?", Integer.class, id) == 0)
            throw notFound();
    }

    private JsonNode parseCriteria(String value) {
        try {
            JsonNode node = mapper.readTree(value);
            if (node == null || !node.isObject()) throw new IllegalArgumentException();
            return node;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "criteria must be a JSON object");
        }
    }

    private Actor actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        String subject = authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt
                ? jwt.getIssuer() + "|" + jwt.getSubject() : "local|" + authentication.getName();
        UUID user = jdbc.queryForObject("SELECT id FROM app_user WHERE issuer_subject=?", UUID.class, subject);
        return new Actor(user);
    }

    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }
    private record Actor(UUID userId) {}
    private record Listing(UUID id, Instant createdAt, Instant updatedAt) {}
    private record Search(UUID id, UUID userId, String criteria, boolean newListing, boolean priceChange) {}
    private record Preference(boolean consent, String channel, int quota) {}
}
