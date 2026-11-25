package ir.khodroban;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
@Testcontainers
class AlertPostgresIntegrationTest {
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
    void reset() {
        jdbc.update("DELETE FROM notification_outbox");
        jdbc.update("DELETE FROM alert_event");
        jdbc.update("DELETE FROM saved_search");
        jdbc.update("DELETE FROM favorite");
        jdbc.update("DELETE FROM alert_preference");
        jdbc.update("DELETE FROM listing_source");
        jdbc.update("DELETE FROM listing");
        jdbc.update("DELETE FROM workspace_member");
        jdbc.update("DELETE FROM workspace");
        jdbc.update("DELETE FROM app_user");
        jdbc.update("UPDATE alert_job_cursor SET cursor_at='1970-01-01T00:00:00Z' WHERE job_name='listing-alerts'");
    }

    @Test
    void favoritesAndAlertsAreOwnedConsentedQuotaLimitedAndIdempotent() throws Exception {
        user("alice");
        String alice = me("alice");
        user("bob");
        String bob = me("bob");
        UUID listing = insertListing("alert-1", "Synthetic Make", "Synthetic Model", "North");

        mvc.perform(post("/api/v1/favorites/" + listing).with(user("alice")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/favorites").with(user("bob")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/favorites").with(user("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].listingId", equalTo(listing.toString())));

        mvc.perform(put("/api/v1/alert-preferences").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent\":true,\"channel\":\"IN_APP\",\"dailyQuota\":1}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/saved-searches").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"north\",\"criteria\":\"{\\\"region\\\":\\\"North\\\"}\","
                                + "\"newListing\":true,\"priceChange\":false}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/ops/alerts/run").with(user("alice").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("OPS_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.generated", equalTo(1)));
        mvc.perform(post("/api/v1/ops/alerts/run")
                        .with(user("alice").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("OPS_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.generated", equalTo(0)));
        mvc.perform(get("/api/v1/alerts").with(user("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status", equalTo("PENDING")));
        mvc.perform(get("/api/v1/alerts").with(user("bob")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        Assertions.assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox", Integer.class));
        Assertions.assertNotNull(alice);
        Assertions.assertNotNull(bob);
    }

    @Test
    void absentConsentAndZeroQuotaDoNotGenerateOutboxRows() throws Exception {
        me("quota-user");
        UUID listing = insertListing("quota-1", "Synthetic Make", "Synthetic Model", "North");
        mvc.perform(put("/api/v1/alert-preferences").with(user("quota-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent\":false,\"channel\":\"IN_APP\",\"dailyQuota\":10}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/saved-searches").with(user("quota-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"all\",\"criteria\":\"{}\",\"newListing\":true,\"priceChange\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/ops/alerts/run").with(user("quota-user"))
                        .with(user("quota-user").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("OPS_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.generated", equalTo(0)));
        Assertions.assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox", Integer.class));
        Assertions.assertNotNull(listing);
    }

    private String me(String user) throws Exception {
        return mvc.perform(get("/api/v1/users/me").with(user(user))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private UUID insertListing(String external, String make, String model, String region) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO listing(id,source_code,external_id,listing_hash,normalized_data,normalized_hash,
                payload_hash,normalized_make,normalized_model,region,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, id, "SYNTHETIC_FIXTURE", external, UUID.randomUUID().toString(),
                "{\"synthetic\":true}", UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                make, model, region);
        return id;
    }
}
