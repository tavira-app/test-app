package ir.khodroban.price;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class PriceController {
    private final PriceService service;

    public PriceController(PriceService service) {
        this.service = service;
    }

    @GetMapping("/listings/{listingId}/price-analysis")
    public PriceService.EstimateView analysis(@PathVariable UUID listingId) {
        return service.analysis(listingId);
    }

    @GetMapping("/price/comparables")
    public PriceService.ComparableResult comparables(
            @RequestParam @Size(max = 120) String make,
            @RequestParam @Size(max = 120) String model,
            @RequestParam(required = false) @Size(max = 120) String trim,
            @RequestParam(required = false) @Min(1886) @Max(2200) Integer year,
            @RequestParam(required = false) @Min(0) Long mileageKm,
            @RequestParam(required = false) @Size(max = 120) String region,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return service.comparables(new PriceService.Criteria(make, model, trim, year,
                mileageKm, region, limit));
    }

    @GetMapping("/price/history")
    public List<PriceService.TrendPoint> history(
            @RequestParam @Size(max = 120) String make,
            @RequestParam @Size(max = 120) String model,
            @RequestParam(required = false) @Size(max = 120) String region) {
        return service.history(make, model, region);
    }
}
