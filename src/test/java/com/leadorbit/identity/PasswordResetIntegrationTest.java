package com.leadorbit.identity;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.leadorbit.identity.service.PasswordResetService;
import com.leadorbit.notification.domain.NotificationOutbox;
import com.leadorbit.notification.repository.NotificationOutboxRepository;

@SpringBootTest(properties = {
    "app.security.bootstrap.super-admin.email=admin@test.local",
    "app.security.bootstrap.super-admin.password=SuperSecret-1234567890",
    "app.security.bootstrap.super-admin.name=Test Admin",
    "server.servlet.context-path=",
    "app.scheduling.enabled=false",
    "app.mail.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class PasswordResetIntegrationTest {

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

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private NotificationOutboxRepository outboxRepository;

    @Test
    void resetWorksTokenIsSingleUseAndAllSessionsDie() throws Exception {
        String admin = signIn(ADMIN_EMAIL, ADMIN_PASSWORD).path("accessToken").asText();
        createUser(admin, "resetflow", "resetflow@test.local", "OldPassword-1234567890");

        // 1. Request a reset through the public endpoint; the email goes to the outbox.
        mockMvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"resetflow@test.local\"}"))
            .andExpect(status().isOk());

        NotificationOutbox row = outboxRepository.findAll().stream()
            .filter(o -> "resetflow@test.local".equalsIgnoreCase(o.getRecipientEmail()))
            .filter(o -> "password-reset".equals(o.getEventType()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected password-reset outbox row"));
        assertThat(row.getSubject()).contains("Reset your LeadOrbit password");
        assertThat(row.getBody()).contains("http://localhost:5173/reset-password?token=");

        // 2. Sign in once so there is a live refresh session.
        String oldRefresh = signIn("resetflow@test.local", "OldPassword-1234567890")
            .path("refreshToken").asText();

        // 3. Complete the reset (raw token obtained at the service layer, as the API never leaks it).
        String rawToken = passwordResetService.requestReset("resetflow@test.local").orElseThrow();
        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"NewPassword-1234567890\"}"))
            .andExpect(status().isOk());

        // 4. The old refresh session was revoked.
        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
            .andExpect(status().isUnauthorized());

        // 5. New password works, old one does not.
        signIn("resetflow@test.local", "NewPassword-1234567890");
        mockMvc.perform(post("/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"resetflow@test.local\",\"password\":\"OldPassword-1234567890\"}"))
            .andExpect(status().isUnauthorized());

        // 6. The token is single-use.
        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"YetAnother-1234567890\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPasswordNeverRevealsWhetherTheEmailExists() throws Exception {
        mockMvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody-here@test.local\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message")
                .value("If that email exists, a reset link is on its way"));

        assertThat(outboxRepository.findAll().stream()
            .filter(o -> "nobody-here@test.local".equalsIgnoreCase(o.getRecipientEmail())))
            .isEmpty();
    }

    // ------------------------------------------------------------------ fixture

    private void createUser(String admin, String name, String email, String password)
        throws Exception {
        mockMvc.perform(post("/users")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\","
                    + "\"password\":\"" + password + "\",\"roles\":[\"VIEWER\"]}"))
            .andExpect(status().isOk());
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