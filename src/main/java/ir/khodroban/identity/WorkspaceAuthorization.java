package ir.khodroban.identity;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Central workspace membership check; inaccessible and absent workspaces are indistinguishable. */
@Component
public class WorkspaceAuthorization {
    private final JdbcTemplate jdbc;

    public WorkspaceAuthorization(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void requireMember(UUID workspaceId, UUID userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workspace_member WHERE workspace_id = ? AND user_id = ?",
                Integer.class, workspaceId, userId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    public void requireRole(UUID workspaceId, UUID userId, String requiredRole) {
        String role = jdbc.query(
                "SELECT role FROM workspace_member WHERE workspace_id = ? AND user_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, workspaceId, userId);
        if (role == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!role.equals(requiredRole)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
