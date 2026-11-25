package ir.khodroban.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class IdentityService {
    private final JdbcTemplate jdbc;
    private final WorkspaceAuthorization authorization;

    public IdentityService(JdbcTemplate jdbc, WorkspaceAuthorization authorization) {
        this.jdbc = jdbc;
        this.authorization = authorization;
    }

    @Transactional
    public UserView me() {
        Actor actor = actor();
        var users = jdbc.query(
                "SELECT id, issuer_subject, email, display_name, created_at FROM app_user WHERE issuer_subject = ?",
                (rs, row) -> new UserView(rs.getObject("id", UUID.class),
                        rs.getString("email"), rs.getString("display_name"),
                        rs.getObject("created_at", Instant.class)), actor.subject());
        UserView user;
        if (users.isEmpty()) {
            UUID id = UUID.randomUUID();
            String displayName = actor.displayName();
            jdbc.update("INSERT INTO app_user(id, issuer_subject, email, display_name) VALUES (?, ?, ?, ?)",
                    id, actor.subject(), actor.email(), displayName);
            user = new UserView(id, actor.email(), displayName, Instant.now());
        } else {
            user = users.getFirst();
            if (actor.email() != null) {
                jdbc.update("UPDATE app_user SET email = ? WHERE id = ?", actor.email(), user.id());
                user = new UserView(user.id(), actor.email(), user.displayName(), user.createdAt());
            }
        }
        Integer memberships = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workspace_member WHERE user_id = ?", Integer.class, user.id());
        if (memberships == null || memberships == 0) createWorkspace(user.id(), "Personal workspace");
        return user;
    }

    @Transactional
    public List<WorkspaceView> workspaces() {
        UserView user = me();
        return jdbc.query("""
                SELECT w.id, w.name, w.created_at, m.role
                FROM workspace w JOIN workspace_member m ON m.workspace_id = w.id
                WHERE m.user_id = ? ORDER BY w.created_at, w.id
                """, (rs, row) -> new WorkspaceView(rs.getObject("id", UUID.class),
                rs.getString("name"), rs.getString("role"),
                rs.getObject("created_at", Instant.class)), user.id());
    }

    @Transactional
    public WorkspaceView createWorkspace(String name) {
        UserView user = me();
        return createWorkspace(user.id(), name);
    }

    @Transactional(readOnly = true)
    public WorkspaceView workspace(UUID id) {
        UserView user = me();
        authorization.requireMember(id, user.id());
        return jdbc.query("""
                SELECT w.id, w.name, w.created_at, m.role
                FROM workspace w JOIN workspace_member m ON m.workspace_id = w.id
                WHERE w.id = ? AND m.user_id = ?
                """, rs -> rs.next() ? new WorkspaceView(rs.getObject("id", UUID.class),
                rs.getString("name"), rs.getString("role"),
                rs.getObject("created_at", Instant.class)) : null, id, user.id());
    }

    private WorkspaceView createWorkspace(UUID userId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO workspace(id, name, created_by) VALUES (?, ?, ?)", id, name, userId);
        jdbc.update("INSERT INTO workspace_member(workspace_id, user_id, role) VALUES (?, ?, 'OWNER')",
                id, userId);
        return new WorkspaceView(id, name, "OWNER", Instant.now());
    }

    private Actor actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getIssuer() + "|" + jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            String name = jwt.getClaimAsString("name");
            if (name == null || name.isBlank()) name = email;
            if (name == null || name.isBlank()) name = jwt.getSubject();
            return new Actor(subject, email, name == null ? email : name);
        }
        String name = authentication.getName();
        String email = authentication.getDetails() instanceof String value ? value : null;
        return new Actor("local|" + name, email, email == null ? name : email);
    }

    public record UserView(UUID id, String email, String displayName, Instant createdAt) {}
    public record WorkspaceView(UUID id, String name, String role, Instant createdAt) {}
    private record Actor(String subject, String email, String displayName) {}
}
