package ir.khodroban.insights;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1")
public class ComparisonInspectionFeedbackController {
    private final ComparisonInspectionFeedbackService service;

    public ComparisonInspectionFeedbackController(ComparisonInspectionFeedbackService service) {
        this.service = service;
    }

    @PostMapping("/comparisons")
    public ComparisonView createComparison(@Valid @RequestBody ComparisonRequest request) {
        return service.createComparison(request);
    }

    @GetMapping("/comparisons")
    public List<ComparisonView> comparisons() {
        return service.comparisons();
    }

    @GetMapping("/comparisons/{id}")
    public ComparisonView comparison(@PathVariable UUID id) {
        return service.comparison(id);
    }

    @DeleteMapping("/comparisons/{id}")
    public void deleteComparison(@PathVariable UUID id) {
        service.deleteComparison(id);
    }

    @PostMapping("/inspection-checklists")
    public ChecklistView createChecklist(@Valid @RequestBody ChecklistRequest request) {
        return service.createChecklist(request);
    }

    @GetMapping("/inspection-checklists")
    public List<ChecklistView> checklists() {
        return service.checklists();
    }

    @GetMapping("/inspection-checklists/{id}")
    public ChecklistView checklist(@PathVariable UUID id) {
        return service.checklist(id);
    }

    @PostMapping("/listings/{listingId}/inspection-reports")
    public ReportView createReport(@PathVariable UUID listingId,
                                   @Valid @RequestBody ReportRequest request) {
        return service.createReport(listingId, request);
    }

    @GetMapping("/listings/{listingId}/inspection-reports")
    public List<ReportView> reports(@PathVariable UUID listingId) {
        return service.reports(listingId);
    }

    @PostMapping("/feedback")
    public FeedbackView createFeedback(@Valid @RequestBody FeedbackRequest request) {
        return service.createFeedback(request);
    }

    @GetMapping("/feedback")
    public List<FeedbackView> feedback() {
        return service.feedback();
    }

    public record ComparisonRequest(@NotBlank @Size(max = 160) String name,
                                    @NotEmpty @Size(min = 2, max = 5)
                                    List<@NotNull UUID> listingIds,
                                    @Size(max = 4000) String notes) {}

    public record ChecklistRequest(@NotBlank @Size(max = 160) String name,
                                   @NotBlank @Size(max = 20000) String itemsJson,
                                   @Size(max = 120) String provenanceSource) {}

    public record ReportRequest(@NotBlank @Size(max = 20000) String reportJson,
                                @NotBlank @Size(max = 120) String provenanceSource,
                                @NotNull @DecimalMin("0.0") @DecimalMax("1.0")
                                java.math.BigDecimal confidence) {}

    public record FeedbackRequest(@NotBlank @Size(max = 40) String targetType,
                                  @NotNull UUID targetId,
                                  @NotBlank @Size(max = 40) String category,
                                  @Size(max = 4000) String comment,
                                  @Min(1) @Max(5) Integer rating) {}

    public record ComparisonView(UUID id, String name, String notes,
                                 List<ComparisonItemView> items,
                                 java.time.Instant createdAt) {}
    public record ComparisonItemView(UUID listingId, String title, String make, String model,
                                     Integer year, Long priceAmount, String priceCurrency,
                                     String source, String unavailableFields) {}
    public record ChecklistView(UUID id, String name, String itemsJson,
                                String provenanceSource, UUID authorId,
                                java.time.Instant createdAt) {}
    public record ReportView(UUID id, UUID listingId, String reportJson,
                             String provenanceSource, java.math.BigDecimal confidence,
                             UUID authorId, java.time.Instant createdAt) {}
    public record FeedbackView(UUID id, String targetType, UUID targetId, String category,
                               String comment, Integer rating, UUID authorId,
                               java.time.Instant createdAt) {}
}
