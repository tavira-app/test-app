package ir.khodroban;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ir.khodroban.price.PriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
@Testcontainers
class PricePostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM price_estimate");
        jdbc.update("DELETE FROM comparable_set");
        jdbc.update("DELETE FROM listing");
    }

    @Test
    void analysisSelectsPersistedAskingComparablesAndLabelsOutlierAndDelta() throws Exception {
        UUID target = listing("target", 150L, 1000L);
        listing("c1", 100L, 1000L);
        listing("c2", 110L, 1000L);
        listing("c3", 120L, 1000L);
        listing("outlier", 10000L, 1000L);

        mvc.perform(get("/api/v1/listings/" + target + "/price-analysis").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("AVAILABLE")))
                .andExpect(jsonPath("$.label", equalTo("ASKING_PRICE_ESTIMATE")))
                .andExpect(jsonPath("$.median", equalTo(110.0)))
                .andExpect(jsonPath("$.sampleCount", equalTo(3)))
                .andExpect(jsonPath("$.deltaAmount", equalTo(40.0)))
                .andExpect(jsonPath("$.dataType", equalTo("ASKING")));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM price_estimate", Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals("ASKING", jdbc.queryForObject(
                "SELECT data_type FROM comparable_set", String.class));
    }

    @Test
    void insufficientDataIsExplicitAndHistoryIsGroupedByDate() throws Exception {
        UUID target = listing("only-target", 150L, 1000L);
        listing("only-comparable", 100L, 1000L);
        mvc.perform(get("/api/v1/listings/" + target + "/price-analysis").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("INSUFFICIENT_DATA")))
                .andExpect(jsonPath("$.confidence", equalTo(0.0)))
                .andExpect(jsonPath("$.median", equalTo(100.0)))
                .andExpect(jsonPath("$.limitation").isNotEmpty());
        mvc.perform(get("/api/v1/price/history").with(user("reader"))
                        .param("make", "Synthetic Make").param("model", "Synthetic Model"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].dataType", equalTo("ASKING")));
    }

    private UUID listing(String external, long price, long mileage) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO listing(id,source_code,external_id,listing_hash,normalized_data,
                    normalized_hash,payload_hash,listing_title,normalized_make,normalized_model,
                    normalized_trim,model_year,mileage_km,asking_price_amount,asking_price_currency,
                    region,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'North',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, id, "SYNTHETIC_FIXTURE", external, UUID.randomUUID().toString(),
                "{\"synthetic\":true}", UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                external, "Synthetic Make", "Synthetic Model", "Synthetic Trim", 2022,
                mileage, price, "IRR");
        return id;
    }
}
