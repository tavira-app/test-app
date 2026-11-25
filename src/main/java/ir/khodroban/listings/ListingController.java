package ir.khodroban.listings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/listings")
public class ListingController {
    private final ListingService listings;

    public ListingController(ListingService listings) {
        this.listings = listings;
    }

    @GetMapping
    public ListingService.PageView search(
            @RequestParam(required = false) @Size(max = 120) String make,
            @RequestParam(required = false) @Size(max = 120) String model,
            @RequestParam(required = false) @Size(max = 120) String trim,
            @RequestParam(required = false) @Min(1886) @Max(2200) Integer yearFrom,
            @RequestParam(required = false) @Min(1886) @Max(2200) Integer yearTo,
            @RequestParam(required = false) @Min(0) Long mileageFrom,
            @RequestParam(required = false) @Min(0) Long mileageTo,
            @RequestParam(required = false) @Min(1) Long priceFrom,
            @RequestParam(required = false) @Min(1) Long priceTo,
            @RequestParam(required = false) @Size(max = 120) String region,
            @RequestParam(required = false) @Size(max = 60) String fuel,
            @RequestParam(required = false) @Size(max = 60) String transmission,
            @RequestParam(required = false) @Size(max = 60) String body,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return listings.search(new ListingService.SearchRequest(make, model, trim, yearFrom, yearTo,
                mileageFrom, mileageTo, priceFrom, priceTo, region, fuel, transmission, body,
                page, size, sort, direction));
    }

    @GetMapping("/{id}")
    public ListingService.DetailView detail(@PathVariable UUID id) {
        return listings.detail(id);
    }

    @GetMapping("/{id}/history")
    public ListingService.HistoryView history(@PathVariable UUID id) {
        return listings.history(id);
    }

    @GetMapping("/{id}/media")
    public java.util.List<ListingService.MediaView> media(@PathVariable UUID id) {
        return listings.media(id);
    }
}
