package ir.khodroban;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
class FoundationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("UP")));
    }

    @Test
    void apiRequiresAuthenticationWithProblemDetails() throws Exception {
        mvc.perform(get("/api/v1/listings"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", equalTo("AUTHENTICATION_REQUIRED")))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void flywayCreatesConstrainedFoundationTable() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'APP_METADATA'",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO app_metadata(metadata_key, metadata_value) VALUES (' ', 'x')"));
    }

    @Test
    void localProfileIdentityCreatesUserAndPersonalWorkspace() throws Exception {
        mvc.perform(get("/api/v1/users/me").header("X-Khodroban-User", "local-smoke")
                        .header("X-Khodroban-Email", "smoke@example.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", equalTo("smoke@example.test")));
        mvc.perform(get("/api/v1/workspaces").header("X-Khodroban-User", "local-smoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role", equalTo("OWNER")));
    }

    @Test
    void workspaceIsNotVisibleToAnotherAuthenticatedUser() throws Exception {
        var result = mvc.perform(get("/api/v1/users/me").with(user("alice")))
                .andExpect(status().isOk()).andReturn();
        String userJson = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode userNode =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(userJson);
        String workspaceId = jdbc.queryForObject(
                "SELECT id FROM workspace WHERE created_by = ?",
                (rs, row) -> rs.getString(1), java.util.UUID.fromString(userNode.get("id").asText()));
        mvc.perform(get("/api/v1/workspaces/" + workspaceId).with(user("bob")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_NOT_FOUND")));
        mvc.perform(get("/api/v1/workspaces").with(user("bob")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", not(equalTo(workspaceId))));
    }

    @Test
    void workspaceNamesAreValidated() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/workspaces")
                        .with(user("validator")).contentType("application/json").content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));
    }
}
