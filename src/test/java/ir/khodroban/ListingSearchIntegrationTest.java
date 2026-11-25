package ir.khodroban;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
class ListingSearchIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private UUID listingId;

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM listing_source");
        jdbc.update("DELETE FROM listing_snapshot");
        jdbc.update("DELETE FROM listing");
        listingId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO listing(id,source_code,external_id,listing_hash,normalized_data,
                    normalized_hash,payload_hash,listing_title,normalized_make,normalized_model,
                    normalized_trim,model_year,mileage_km,asking_price_amount,asking_price_currency,
                    city,region,fuel_type,transmission,body_type)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, listingId, "SYNTHETIC_FIXTURE", "search-1", "hash-search-1", "{\"fixture\":true}",
                "normalized-search-1", "payload-search-1", "Synthetic sedan", "Synthetic Make",
                "Synthetic Model", "Synthetic Trim", 2022, 25000, 850000L, "IRR", "Tehran",
                "North", "petrol", "automatic", "sedan");
        UUID provider = jdbc.queryForObject("SELECT id FROM source_provider WHERE code=?",
                UUID.class, "SYNTHETIC_FIXTURE");
        jdbc.update("INSERT INTO listing_source(listing_id,provider_id,external_id) VALUES (?,?,?)",
                listingId, provider, "search-1");
        jdbc.update("INSERT INTO listing_snapshot(id,listing_id,normalized_data,normalized_hash,source_code) "
                + "VALUES (?,?,?,?,?)", UUID.randomUUID(), listingId, "{\"fixture\":true}",
                "snapshot-search-1", "SYNTHETIC_FIXTURE");
    }

    @Test
    void filtersPersistedListingsAndExposesFreshnessAndHistory() throws Exception {
        mvc.perform(get("/api/v1/listings").with(user("reader"))
                        .param("make", "Synthetic Make").param("yearFrom", "2020")
                        .param("priceTo", "900000").param("region", "North")
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", equalTo(listingId.toString())))
                .andExpect(jsonPath("$.content[0].source", equalTo("SYNTHETIC_FIXTURE")))
                .andExpect(jsonPath("$.totalElements", equalTo(1)));
        mvc.perform(get("/api/v1/listings/" + listingId).with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listing.title", equalTo("Synthetic sedan")))
                .andExpect(jsonPath("$.listing.lastReceivedAt").exists());
        mvc.perform(get("/api/v1/listings/" + listingId + "/history").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshots", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void paginationAndInvalidSortAreBoundedAndProtected() throws Exception {
        mvc.perform(get("/api/v1/listings"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/listings").with(user("reader")).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));
        mvc.perform(get("/api/v1/listings").with(user("reader")).param("sort", "sql"))
                .andExpect(status().isBadRequest());
    }
}
