package com.leadorbit.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end auth + dynamic DB-driven RBAC flow against a real PostgreSQL via Testcontainers.
 */
@SpringBootTest(properties = {
    "app.security.bootstrap.super-admin.email=admin@test.local",
    "app.security.bootstrap.super-admin.password=SuperSecret-1234567890",
    "app.security.bootstrap.super-admin.name=Test Admin",
    "server.servlet.context-path="
})
@AutoConfigureMockMvc
@Testcontainers
class AuthIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "SuperSecret-1234567890";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void signInWithInvalidCredentialsReturns401() throws Exception {
        mockMvc.perform(post("/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"wrong-password\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void signInReturnsTokensAndAccessTokenProtectsEndpoints() throws Exception {
        JsonNode tokens = signIn(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(tokens.path("accessToken").asText()).isNotBlank();
        assertThat(tokens.path("refreshToken").asText()).isNotBlank();
        assertThat(tokens.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(tokens.path("expiresInSeconds").asLong()).isPositive();
        assertThat(tokens.path("user").path("email").asText()).isEqualTo(ADMIN_EMAIL);
        assertThat(tokens.path("user").path("roles").toString()).contains("SUPER_ADMIN");

        String accessToken = tokens.path("accessToken").asText();
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value(ADMIN_EMAIL));
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/users"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void refreshRotatesTokenAndRejectedReuseRevokesFamily() throws Exception {
        JsonNode first = signIn(ADMIN_EMAIL, ADMIN_PASSWORD);
        String oldRefresh = first.path("refreshToken").asText();

        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode rotated = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String newRefresh = rotated.path("data").path("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        // Reusing the already-consumed token must fail and revoke the whole family.
        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + newRefresh + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        JsonNode tokens = signIn(ADMIN_EMAIL, ADMIN_PASSWORD);
        String refresh = tokens.path("refreshToken").asText();

        mockMvc.perform(post("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void dbDrivenPermissionsAreEnforcedOnEndpoints() throws Exception {
        String adminAccess = signIn(ADMIN_EMAIL, ADMIN_PASSWORD).path("accessToken").asText();

        // Admin (SUPER_ADMIN) can create users.
        String createBody = "{\"name\":\"Viewer One\",\"email\":\"viewer@test.local\","
            + "\"password\":\"ViewerPass-1234567890\",\"roles\":[\"VIEWER\"]}";
        mockMvc.perform(post("/users")
                .header("Authorization", "Bearer " + adminAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("viewer@test.local"));

        // The VIEWER role lacks user.read and audit.read.
        JsonNode viewerTokens = signIn("viewer@test.local", "ViewerPass-1234567890");
        String viewerAccess = viewerTokens.path("accessToken").asText();

        mockMvc.perform(get("/users").header("Authorization", "Bearer " + viewerAccess))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/audit-events").header("Authorization", "Bearer " + viewerAccess))
            .andExpect(status().isForbidden());

        // Admin still has access.
        mockMvc.perform(get("/users").header("Authorization", "Bearer " + adminAccess))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(2));
    }

    private JsonNode signIn(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}