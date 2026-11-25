package ir.khodroban.alerts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AlertController {
    private final AlertService service;

    public AlertController(AlertService service) { this.service = service; }

    @PostMapping("/favorites/{listingId}")
    public FavoriteView favorite(@PathVariable UUID listingId) {
        return service.favorite(listingId);
    }

    @DeleteMapping("/favorites/{listingId}")
    public void unfavorite(@PathVariable UUID listingId) {
        service.unfavorite(listingId);
    }

    @GetMapping("/favorites")
    public List<FavoriteView> favorites() { return service.favorites(); }

    @PostMapping("/saved-searches")
    public SavedSearchView save(@Valid @RequestBody SavedSearchRequest request) {
        return service.saveSearch(request);
    }

    @GetMapping("/saved-searches")
    public List<SavedSearchView> searches() { return service.searches(); }

    @DeleteMapping("/saved-searches/{id}")
    public void deleteSearch(@PathVariable UUID id) { service.deleteSearch(id); }

    @PutMapping("/alert-preferences")
    public PreferenceView preference(@Valid @RequestBody PreferenceRequest request) {
        return service.preference(request);
    }

    @GetMapping("/alert-preferences")
    public PreferenceView preference() { return service.preference(); }

    @GetMapping("/alerts")
    public List<OutboxView> alerts(@RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return service.alerts(limit);
    }

    @PostMapping("/ops/alerts/run")
    @PreAuthorize("hasAnyAuthority('OPS_ADMIN','OPS_INGEST')")
    public JobView run() { return service.generateAlerts(); }

    @PostMapping("/ops/alerts/deliver")
    @PreAuthorize("hasAnyAuthority('OPS_ADMIN','OPS_INGEST')")
    public DeliveryJobView deliver() {
        return service.deliverInApp();
    }

    public record SavedSearchRequest(@NotBlank @Size(max = 120) String name,
                                     @NotBlank @Size(max = 4000) String criteria,
                                     boolean newListing, boolean priceChange) {}
    public record PreferenceRequest(boolean consent, @NotNull Channel channel,
                                    @Min(0) @Max(10000) int dailyQuota) {}
    public enum Channel { IN_APP }
    public record FavoriteView(UUID listingId, java.time.Instant createdAt) {}
    public record SavedSearchView(UUID id, String name, String criteria, boolean active,
                                  boolean newListing, boolean priceChange, java.time.Instant createdAt) {}
    public record PreferenceView(boolean consent, Channel channel, int dailyQuota) {}
    public record OutboxView(UUID id, UUID savedSearchId, UUID listingId, String eventType,
                             String status, int attempts, java.time.Instant createdAt) {}
    public record JobView(UUID id, String status, int generated, java.time.Instant completedAt) {}
    public record DeliveryJobView(UUID id, String status, int delivered, java.time.Instant completedAt) {}
}
