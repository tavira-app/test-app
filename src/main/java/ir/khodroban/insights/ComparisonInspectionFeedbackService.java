package ir.khodroban.insights;

import ir.khodroban.insights.ComparisonInspectionFeedbackController.*;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ComparisonInspectionFeedbackService {
    private final JdbcTemplate jdbc;

    public ComparisonInspectionFeedbackService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ComparisonView createComparison(ComparisonRequest request) {
        Actor actor = actor();
        LinkedHashSet<UUID> ids = new LinkedHashSet<>(request.listingIds());
        if (ids.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A comparison requires distinct listing IDs.");
        }
        for (UUID id : ids) requireListing(id);
        UUID id = UUID.randomUUID();
        UUID workspace = workspace(actor.id());
        jdbc.update("""
                INSERT INTO vehicle_comparison(id,user_id,workspace_id,name,notes)
                VALUES (?,?,?,?,?)
                """, id, actor.id(), workspace, request.name().trim(), blank(request.notes()));
        int position = 0;
        for (UUID listingId : ids) {
            jdbc.update("""
                    INSERT INTO vehicle_comparison_item(comparison_id,listing_id,position)
                    VALUES (?,?,?)
                    """, id, listingId, position++);
        }
        return comparison(id);
    }

    @Transactional(readOnly = true)
    public List<ComparisonView> comparisons() {
        UUID user = actor().id();
        return jdbc.query("""
                SELECT id,name,notes,created_at FROM vehicle_comparison
                WHERE user_id=? ORDER BY created_at DESC
                """, (rs, row) -> comparisonRow(rs), user).stream()
                .map(row -> withItems(row, items(row.id()))).toList();
    }

    @Transactional(readOnly = true)
    public ComparisonView comparison(UUID id) {
        UUID user = actor().id();
        ComparisonRow row = jdbc.query("""
                SELECT id,name,notes,created_at FROM vehicle_comparison
                WHERE id=? AND user_id=?
                """, rs -> rs.next() ? comparisonRow(rs) : null, id, user);
        if (row == null) throw notFound();
        return withItems(row, items(id));
    }

    @Transactional
    public void deleteComparison(UUID id) {
        if (jdbc.update("DELETE FROM vehicle_comparison WHERE id=? AND user_id=?",
                id, actor().id()) == 0) throw notFound();
    }

    @Transactional
    public ChecklistView createChecklist(ChecklistRequest request) {
        Actor actor = actor();
        validateJsonObjectOrArray(request.itemsJson(), "itemsJson");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inspection_checklist(id,user_id,workspace_id,name,items_json,
                    provenance_source,author_id)
                VALUES (?,?,?,?,?,?,?)
                """, id, actor.id(), workspace(actor.id()), request.name().trim(),
                request.itemsJson(), source(request.provenanceSource()), actor.id());
        return checklist(id);
    }

    @Transactional(readOnly = true)
    public List<ChecklistView> checklists() {
        return jdbc.query("""
                SELECT id,name,items_json,provenance_source,author_id,created_at
                FROM inspection_checklist WHERE user_id=? ORDER BY created_at DESC
                """, (rs, row) -> checklistRow(rs), actor().id());
    }

    @Transactional(readOnly = true)
    public ChecklistView checklist(UUID id) {
        ChecklistView value = jdbc.query("""
                SELECT id,name,items_json,provenance_source,author_id,created_at
                FROM inspection_checklist WHERE id=? AND user_id=?
                """, rs -> rs.next() ? checklistRow(rs) : null, id, actor().id());
        if (value == null) throw notFound();
        return value;
    }

    @Transactional
    public ReportView createReport(UUID listingId, ReportRequest request) {
        Actor actor = actor();
        requireListing(listingId);
        validateJsonObjectOrArray(request.reportJson(), "reportJson");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inspection_report(id,listing_id,user_id,workspace_id,report_json,
                    provenance_source,confidence,author_id)
                VALUES (?,?,?,?,?,?,?,?)
                """, id, listingId, actor.id(), workspace(actor.id()), request.reportJson(),
                source(request.provenanceSource()), request.confidence(), actor.id());
        return report(id);
    }

    @Transactional(readOnly = true)
    public List<ReportView> reports(UUID listingId) {
        requireListing(listingId);
        return jdbc.query("""
                SELECT id,listing_id,report_json,provenance_source,confidence,author_id,created_at
                FROM inspection_report WHERE listing_id=? AND user_id=?
                ORDER BY created_at DESC
                """, (rs, row) -> reportRow(rs), listingId, actor().id());
    }

    @Transactional
    public FeedbackView createFeedback(FeedbackRequest request) {
        Actor actor = actor();
        String type = request.targetType().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("LISTING", "COMPARISON", "INSPECTION_REPORT").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported feedback target.");
        }
        if (request.comment() == null && request.rating() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Feedback requires a comment or rating.");
        }
        verifyTarget(type, request.targetId(), actor.id());
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO user_feedback(id,user_id,workspace_id,target_type,target_id,
                    category,comment,rating,author_id)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, id, actor.id(), workspace(actor.id()), type, request.targetId(),
                request.category().trim(), blank(request.comment()), request.rating(), actor.id());
        return feedback(id);
    }

    @Transactional(readOnly = true)
    public List<FeedbackView> feedback() {
        return jdbc.query("""
                SELECT id,target_type,target_id,category,comment,rating,author_id,created_at
                FROM user_feedback WHERE user_id=? ORDER BY created_at DESC
                """, (rs, row) -> feedbackRow(rs), actor().id());
    }

    private List<ComparisonItemView> items(UUID comparisonId) {
        return jdbc.query("""
                SELECT i.listing_id,l.listing_title,l.normalized_make,l.normalized_model,
                       l.model_year,l.asking_price_amount,l.asking_price_currency,l.source_code
                FROM vehicle_comparison_item i JOIN listing l ON l.id=i.listing_id
                WHERE i.comparison_id=? ORDER BY i.position
                """, (rs, row) -> new ComparisonItemView(
                rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getString(4), (Integer) rs.getObject(5),
                rs.getObject(6, Long.class), rs.getString(7), rs.getString(8),
                unavailable(rs)), comparisonId);
    }

    private String unavailable(java.sql.ResultSet rs) throws java.sql.SQLException {
        List<String> fields = new ArrayList<>();
        if (rs.getString("normalized_make") == null) fields.add("make");
        if (rs.getString("normalized_model") == null) fields.add("model");
        if (rs.getObject("model_year") == null) fields.add("year");
        if (rs.getObject("asking_price_amount") == null) fields.add("askingPrice");
        return String.join(",", fields);
    }

    private ComparisonRow comparisonRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ComparisonRow(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getString(3), rs.getTimestamp(4).toInstant());
    }

    private ComparisonView withItems(ComparisonRow row, List<ComparisonItemView> items) {
        return new ComparisonView(row.id(), row.name(), row.notes(), items, row.createdAt());
    }

    private ChecklistView checklistRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ChecklistView(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class),
                rs.getTimestamp(6).toInstant());
    }

    private ReportView reportRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ReportView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), rs.getBigDecimal(5),
                rs.getObject(6, UUID.class), rs.getTimestamp(7).toInstant());
    }

    private FeedbackView feedbackRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FeedbackView(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5),
                (Integer) rs.getObject(6), rs.getObject(7, UUID.class),
                rs.getTimestamp(8).toInstant());
    }

    private FeedbackView feedback(UUID id) {
        return jdbc.query("""
                SELECT id,target_type,target_id,category,comment,rating,author_id,created_at
                FROM user_feedback WHERE id=? AND user_id=?
                """, rs -> rs.next() ? feedbackRow(rs) : null, id, actor().id());
    }

    private ReportView report(UUID id) {
        return jdbc.query("""
                SELECT id,listing_id,report_json,provenance_source,confidence,author_id,created_at
                FROM inspection_report WHERE id=? AND user_id=?
                """, rs -> rs.next() ? reportRow(rs) : null, id, actor().id());
    }

    private void verifyTarget(String type, UUID id, UUID user) {
        String table = switch (type) {
            case "LISTING" -> "listing";
            case "COMPARISON" -> "vehicle_comparison";
            default -> "inspection_report";
        };
        String owner = type.equals("LISTING") ? "" : " AND user_id=?";
        Integer count = type.equals("LISTING")
                ? jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id=?", Integer.class, id)
                : jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id=?" + owner,
                Integer.class, id, user);
        if (count == null || count == 0) throw notFound();
    }

    private void requireListing(UUID id) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM listing WHERE id=?",
                Integer.class, id) == 0) throw notFound();
    }

    private UUID workspace(UUID user) {
        return jdbc.queryForObject("""
                SELECT workspace_id FROM workspace_member WHERE user_id=?
                ORDER BY joined_at,workspace_id LIMIT 1
                """, UUID.class, user);
    }

    private Actor actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        String subject;
        String email = null;
        String displayName;
        if (auth.getPrincipal() instanceof Jwt jwt) {
            subject = jwt.getIssuer() + "|" + jwt.getSubject();
            email = jwt.getClaimAsString("email");
            displayName = firstNonBlank(jwt.getClaimAsString("name"), email, jwt.getSubject());
        } else {
            subject = "local|" + auth.getName();
            displayName = auth.getName();
        }
        List<UUID> users = jdbc.query("SELECT id FROM app_user WHERE issuer_subject=?",
                (ResultSet rs, int row) -> rs.getObject(1, UUID.class), subject);
        UUID id;
        if (users.isEmpty()) {
            id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO app_user(id,issuer_subject,email,display_name)
                    VALUES (?,?,?,?)
                    """, id, subject, email, displayName);
            UUID workspace = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO workspace(id,name,created_by) VALUES (?,?,?)
                    """, workspace, "Personal workspace", id);
            jdbc.update("""
                    INSERT INTO workspace_member(workspace_id,user_id,role)
                    VALUES (?,?,?)
                    """, workspace, id, "OWNER");
        } else {
            id = users.getFirst();
        }
        return new Actor(id);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "user";
    }

    private void validateJsonObjectOrArray(String value, String field) {
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(value);
            if (node == null || !(node.isObject() || node.isArray())) throw new IllegalArgumentException();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a JSON object or array.");
        }
    }

    private static String source(String value) {
        return value == null || value.isBlank() ? "USER_SUBMITTED" : value.trim();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private record Actor(UUID id) {}
    private record ComparisonRow(UUID id, String name, String notes, Instant createdAt) {}
}
