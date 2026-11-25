package ir.khodroban.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/ops")
public class SourceController {
    private final SourceService sources;

    public SourceController(SourceService sources) {
        this.sources = sources;
    }

    @GetMapping("/sources")
    @PreAuthorize("hasAnyAuthority('OPS_INGEST','OPS_ADMIN')")
    public List<SourceService.SourceView> sources() {
        return sources.sources();
    }

    @PutMapping("/sources/{code}/configuration")
    @PreAuthorize("hasAuthority('OPS_ADMIN')")
    public SourceService.SourceView configure(@PathVariable String code,
                                               @Valid @RequestBody Configuration request) {
        return sources.configure(code, request);
    }

    @PostMapping("/sources/{code}/ingestions")
    @PreAuthorize("hasAnyAuthority('OPS_INGEST','OPS_ADMIN')")
    public SourceService.RunView ingest(@PathVariable String code,
                                        @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 120) String key,
                                        @Valid @RequestBody IngestionRequest request) {
        return sources.ingest(code, key, request);
    }

    public record Configuration(boolean enabled, boolean accessAuthorized,
                                @Size(max = 300) String credentialReference) {}
    public record IngestionRequest(Mode mode, @NotEmpty @Size(max = 100) List<@Valid RecordInput> records) {}
    public record RecordInput(@NotBlank @Size(max = 200) String externalId, JsonNode rawPayload) {}
    public enum Mode { SYNTHETIC_FIXTURE, AUTHORIZED_IMPORT }
}
