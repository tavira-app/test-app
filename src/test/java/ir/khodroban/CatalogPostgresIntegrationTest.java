package ir.khodroban;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
@Testcontainers
class CatalogPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void cleanCatalog() {
        jdbc.update("DELETE FROM vehicle_specification");
        jdbc.update("DELETE FROM vehicle_trim_alias");
        jdbc.update("DELETE FROM vehicle_trim");
        jdbc.update("DELETE FROM vehicle_generation");
        jdbc.update("DELETE FROM vehicle_model");
        jdbc.update("DELETE FROM vehicle_make");
    }

    @Test
    void postgresCatalogPersistsSpecificationsAndResolvesWithProvenance() throws Exception {
        String databaseVersion = jdbc.queryForObject("SELECT version()", String.class);
        org.junit.jupiter.api.Assertions.assertTrue(databaseVersion.startsWith("PostgreSQL"),
                "Catalog integration tests must run against PostgreSQL");

        String makeId = create("/api/v1/catalog/makes", "{\"name\":\"Synthetic Make\",\"countryCode\":\"IR\"}");
        String modelId = create("/api/v1/catalog/models",
                "{\"makeId\":\"" + makeId + "\",\"name\":\"Synthetic Model\"}");
        String generationId = create("/api/v1/catalog/models/" + modelId + "/generations",
                "{\"name\":\"Synthetic Generation\"}");
        String trimId = create("/api/v1/catalog/trims", "{\"modelId\":\"" + modelId
                + "\",\"generationId\":\"" + generationId + "\",\"name\":\"Synthetic Trim\"}");

        mvc.perform(put("/api/v1/catalog/trims/" + trimId + "/specifications").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"engineCc\":1600,\"powerKw\":92,\"fuelType\":\"petrol\","
                                + "\"transmission\":\"manual\",\"consumptionLitresPer100Km\":7.1,"
                                + "\"provenanceSource\":\"SYNTHETIC_TEST_FIXTURE\",\"confidence\":0.95}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provenanceSource", equalTo("SYNTHETIC_TEST_FIXTURE")))
                .andExpect(jsonPath("$.confidence", equalTo(0.95)));
        mvc.perform(post("/api/v1/catalog/trims/" + trimId + "/aliases").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Synthetic Alias\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/catalog/resolve").param("text", "synthetic alias").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("RESOLVED")))
                .andExpect(jsonPath("$.provenance", equalTo("CATALOG_ALIAS")))
                .andExpect(jsonPath("$.confidence", equalTo(1.0)));
        mvc.perform(get("/api/v1/catalog/trims/" + trimId + "/specifications").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engineCc", equalTo(1600)));
        mvc.perform(get("/api/v1/catalog/makes").param("q", "synthetic make").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", equalTo(makeId)));
        mvc.perform(get("/api/v1/catalog/models").param("makeId", makeId).with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", equalTo(modelId)));
        mvc.perform(get("/api/v1/catalog/search").param("q", "synthetic alias").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trimId", equalTo(trimId)));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success=true AND version='3'", Integer.class));
    }

    @Test
    void unknownAndAmbiguousNamesStayUnknownAndWritesRequireAdmin() throws Exception {
        String makeId = create("/api/v1/catalog/makes", "{\"name\":\"Another Synthetic Make\"}");
        String modelId = create("/api/v1/catalog/models",
                "{\"makeId\":\"" + makeId + "\",\"name\":\"First Model\"}");
        create("/api/v1/catalog/trims", "{\"modelId\":\"" + modelId + "\",\"name\":\"Repeated Trim\"}");
        mvc.perform(post("/api/v1/catalog/makes").with(user("ordinary"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Denied\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("ACCESS_DENIED")));
        mvc.perform(get("/api/v1/catalog/resolve").param("text", "not catalogued").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("UNKNOWN")))
                .andExpect(jsonPath("$.confidence", equalTo(0.0)))
                .andExpect(jsonPath("$.provenance", equalTo("NO_CONFIDENT_MATCH")));
        String otherModel = create("/api/v1/catalog/models",
                "{\"makeId\":\"" + makeId + "\",\"name\":\"Second Model\"}");
        create("/api/v1/catalog/trims", "{\"modelId\":\"" + otherModel + "\",\"name\":\"Repeated Trim\"}");
        mvc.perform(get("/api/v1/catalog/resolve").param("text", "repeated trim").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("UNKNOWN")))
                .andExpect(jsonPath("$.detail", equalTo("AMBIGUOUS")));
    }

    @Test
    void invalidCatalogInputIsRejected() throws Exception {
        mvc.perform(post("/api/v1/catalog/makes").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));
    }

    @Test
    void fixtureIngestionIsAuthorizedIdempotentAndPreservesRawRevisions() throws Exception {
        mvc.perform(get("/api/v1/ops/sources").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='DIVAR')].enabled", org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$[?(@.code=='SHEYPOOR')].configured", org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$[?(@.code=='SYNTHETIC_FIXTURE')].type",
                        org.hamcrest.Matchers.contains("SYNTHETIC")));
        mvc.perform(post("/api/v1/ops/sources/SYNTHETIC_FIXTURE/ingestions")
                        .with(user("ordinary")).header("Idempotency-Key", "fixture-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"SYNTHETIC_FIXTURE\",\"records\":["
                                + "{\"externalId\":\"fixture-1\",\"rawPayload\":{\"price\":100}}]}"))
                .andExpect(status().isForbidden());
        String body = "{\"mode\":\"SYNTHETIC_FIXTURE\",\"records\":["
                + "{\"externalId\":\"fixture-1\",\"rawPayload\":{\"price\":100}}]}";
        var first = mvc.perform(post("/api/v1/ops/sources/SYNTHETIC_FIXTURE/ingestions")
                        .with(admin()).header("Idempotency-Key", "fixture-test")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created", equalTo(1)))
                .andExpect(jsonPath("$.replayed", equalTo(false))).andReturn();
        String runId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(first.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/api/v1/ops/sources/SYNTHETIC_FIXTURE/ingestions")
                        .with(admin()).header("Idempotency-Key", "fixture-test")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id", equalTo(runId)))
                .andExpect(jsonPath("$.replayed", equalTo(true)));
        mvc.perform(post("/api/v1/ops/sources/SYNTHETIC_FIXTURE/ingestions")
                        .with(admin()).header("Idempotency-Key", "fixture-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("100", "101")))
                .andExpect(status().isConflict());
        String changed = "{\"mode\":\"SYNTHETIC_FIXTURE\",\"records\":["
                + "{\"externalId\":\"fixture-1\",\"rawPayload\":{\"price\":101}}]}";
        mvc.perform(post("/api/v1/ops/sources/SYNTHETIC_FIXTURE/ingestions")
                        .with(admin()).header("Idempotency-Key", "fixture-update")
                        .contentType(MediaType.APPLICATION_JSON).content(changed))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated", equalTo(1)));
        org.junit.jupiter.api.Assertions.assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw_listing_revision WHERE external_id='fixture-1'", Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM data_ingestion_run WHERE provider_id=(SELECT id FROM source_provider WHERE code='SYNTHETIC_FIXTURE')",
                Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM data_ingestion_record WHERE external_id='fixture-1'", Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM listing WHERE source_code='SYNTHETIC_FIXTURE' AND external_id='fixture-1'",
                Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM listing_source WHERE external_id='fixture-1'", Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM listing_snapshot s JOIN listing l ON l.id=s.listing_id "
                        + "WHERE l.source_code='SYNTHETIC_FIXTURE' AND l.external_id='fixture-1'",
                Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM field_provenance p JOIN listing l ON l.id=p.listing_id "
                        + "WHERE l.source_code='SYNTHETIC_FIXTURE' AND l.external_id='fixture-1'",
                Integer.class));
    }

    @Test
    void externalProvidersCannotBeEnabledWithoutAuthorizedCredentialReference() throws Exception {
        mvc.perform(put("/api/v1/ops/sources/DIVAR/configuration").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"accessAuthorized\":true}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/ops/sources").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='DIVAR')].enabled",
                        org.hamcrest.Matchers.contains(false)));
    }

    private String create(String path, String body) throws Exception {
        var response = mvc.perform(post(path).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                response.getResponse().getContentAsString()).get("id").asText();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return user("catalog-admin").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("OPS_ADMIN"));
    }
}
