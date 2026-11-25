package ir.khodroban.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/makes")
    public List<CatalogService.MakeView> makes(@RequestParam(required = false) @Size(max = 100) String q) {
        return catalog.makes(q);
    }

    @PostMapping("/makes")
    @PreAuthorize("hasAuthority('OPS_ADMIN')")
    public CatalogService.MakeView createMake(@Valid @RequestBody MakeRequest request) {
        return catalog.createMake(request.name(), request.countryCode());
    }

    @GetMapping("/models")
    public List<CatalogService.ModelView> models(@RequestParam(required = false) UUID makeId,
                                                  @RequestParam(required = false) @Size(max = 100) String q) {
        return catalog.models(makeId, q);
    }

    @PostMapping("/models")
    @PreAuthorize("hasAuthority('OPS_ADMIN')")
    public CatalogService.ModelView createModel(@Valid @RequestBody ModelRequest request) {
        return catalog.createModel(request.makeId(), request.name());
    }

    @GetMapping("/models/{modelId}/generations")
    public List<CatalogService.GenerationView> generations(@PathVariable UUID modelId) {
        return catalog.generations(modelId);
    }

    @PostMapping("/models/{modelId}/generations")
    @PreAuthorize("hasAuthority('OPS_ADMIN')")
    public CatalogService.GenerationView createGeneration(@PathVariable UUID modelId,
                                                           @Valid @RequestBody NameRequest request) {
        return catalog.createGeneration(modelId, request.name());
    }

    @GetMapping("/trims")
    public List<CatalogService.TrimView> trims(@RequestParam(required = false) UUID modelId,
                                                @RequestParam(required = false) @Size(max = 100) String q) {
        return catalog.trims(modelId, q);
    }

    @PostMapping("/trims")
    @PreAuthorize("hasAuthority('OPS_ADMIN')")
    public CatalogService.TrimView createTrim(@Valid @RequestBody TrimRequest request) {
        return catalog.createTrim(request.modelId(), request.generationId(), request.name());
    }

    @PostMapping("/trims/{trimId}/aliases")
    @PreAuthorize("hasAuthority('OPS_ADMIN')")
    public CatalogService.AliasView addAlias(@PathVariable UUID trimId,
                                              @Valid @RequestBody NameRequest request) {
        return catalog.addAlias(trimId, request.name());
    }

    @GetMapping("/trims/{trimId}/specifications")
    public CatalogService.SpecificationView specification(@PathVariable UUID trimId) {
        return catalog.specification(trimId);
    }

    @PutMapping("/trims/{trimId}/specifications")
    @PreAuthorize("hasAuthority('OPS_ADMIN')")
    public CatalogService.SpecificationView saveSpecification(@PathVariable UUID trimId,
                                                               @Valid @RequestBody SpecificationRequest request) {
        return catalog.saveSpecification(trimId, request);
    }

    @GetMapping("/search")
    public List<CatalogService.SearchResult> search(@RequestParam @NotBlank @Size(max = 100) String q) {
        return catalog.search(q);
    }

    @GetMapping("/resolve")
    public CatalogService.Resolution resolve(@RequestParam @NotBlank @Size(max = 200) String text) {
        return catalog.resolve(text);
    }

    public record NameRequest(@NotBlank @Size(max = 120) String name) {}
    public record MakeRequest(@NotBlank @Size(max = 120) String name, @Size(max = 2) String countryCode) {}
    public record ModelRequest(@NotNull UUID makeId, @NotBlank @Size(max = 120) String name) {}
    public record TrimRequest(@NotNull UUID modelId, UUID generationId, @NotBlank @Size(max = 120) String name) {}
    public record SpecificationRequest(@Min(0) @Max(20000) Integer engineCc,
                                       @DecimalMin("0.0") @DecimalMax("2000.0") BigDecimal powerKw,
                                       @Size(max = 60) String fuelType, @Size(max = 60) String transmission,
                                       @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal consumptionLitresPer100Km,
                                       @NotBlank @Size(max = 120) String provenanceSource,
                                       @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence) {}
}
