package ir.khodroban.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class IdentityController {
    private final IdentityService identity;

    public IdentityController(IdentityService identity) {
        this.identity = identity;
    }

    @GetMapping("/users/me")
    public IdentityService.UserView me() {
        return identity.me();
    }

    @GetMapping("/workspaces")
    public List<IdentityService.WorkspaceView> workspaces() {
        return identity.workspaces();
    }

    @PostMapping("/workspaces")
    public IdentityService.WorkspaceView createWorkspace(@Valid @RequestBody CreateWorkspace request) {
        return identity.createWorkspace(request.name().trim());
    }

    @GetMapping("/workspaces/{id}")
    public IdentityService.WorkspaceView workspace(@PathVariable UUID id) {
        return identity.workspace(id);
    }

    public record CreateWorkspace(@NotBlank @Size(max = 120) String name) {}
}
