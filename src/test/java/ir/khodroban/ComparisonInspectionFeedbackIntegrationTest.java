package ir.khodroban;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
class ComparisonInspectionFeedbackIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    UUID first;
    UUID second;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM user_feedback");
        jdbc.update("DELETE FROM inspection_report");
        jdbc.update("DELETE FROM inspection_checklist");
        jdbc.update("DELETE FROM vehicle_comparison_item");
        jdbc.update("DELETE FROM vehicle_comparison");
        jdbc.update("DELETE FROM listing");
        first = listing("one");
        second = listing("two");
    }

    @Test
    void ownedComparisonChecklistReportAndFeedbackPersist() throws Exception {
        mvc.perform(get("/api/v1/users/me").with(user("owner"))).andExpect(status().isOk());
        String body = "{\"name\":\"Shortlist\",\"listingIds\":[\"" + first + "\",\"" + second
                + "\"],\"notes\":\"synthetic comparison\"}";
        mvc.perform(post("/api/v1/comparisons").with(user("owner"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].unavailableFields", notNullValue()));
        mvc.perform(post("/api/v1/inspection-checklists").with(user("owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Visit\",\"itemsJson\":\"[{\\\"name\\\":\\\"brakes\\\"}]\","
                                + "\"provenanceSource\":\"USER_SUBMITTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.provenanceSource",
                        equalTo("USER_SUBMITTED")));
        mvc.perform(post("/api/v1/listings/" + first + "/inspection-reports").with(user("owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportJson\":\"{\\\"brakes\\\":\\\"needs review\\\"}\","
                                + "\"provenanceSource\":\"EXPERT_IMPORT\",\"confidence\":0.8}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confidence", equalTo(0.8)));
        mvc.perform(post("/api/v1/feedback").with(user("owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"LISTING\",\"targetId\":\"" + first
                                + "\",\"category\":\"DATA_ERROR\",\"comment\":\"year unavailable\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authorId", notNullValue()));
        mvc.perform(get("/api/v1/feedback").with(user("other")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void validationRejectsNegativeConfidenceAndEmptyComparison() throws Exception {
        mvc.perform(post("/api/v1/comparisons").with(user("validator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"bad\",\"listingIds\":[\"" + first + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));
        mvc.perform(post("/api/v1/listings/" + first + "/inspection-reports").with(user("validator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportJson\":\"{}\",\"provenanceSource\":\"TEST\",\"confidence\":-0.1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));
        mvc.perform(post("/api/v1/feedback").with(user("validator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"LISTING\",\"targetId\":\"" + first
                                + "\",\"category\":\"DATA_ERROR\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void comparisonIsNotReadableByAnotherUser() throws Exception {
        mvc.perform(get("/api/v1/users/me").with(user("alice"))).andExpect(status().isOk());
        String response = mvc.perform(post("/api/v1/comparisons").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Private\",\"listingIds\":[\"" + first + "\",\"" + second + "\"]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asText();
        mvc.perform(get("/api/v1/comparisons/" + id).with(user("bob")))
                .andExpect(status().isNotFound());
    }

    private UUID listing(String external) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO listing(id,source_code,external_id,listing_hash,normalized_data,
                    normalized_hash,payload_hash,listing_title,normalized_make,normalized_model)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, id, "SYNTHETIC_FIXTURE", external, UUID.randomUUID().toString(),
                "{\"synthetic\":true}", UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "Synthetic " + external, "Synthetic Make", "Synthetic Model");
        return id;
    }
}
