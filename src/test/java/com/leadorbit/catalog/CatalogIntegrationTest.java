package com.leadorbit.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Catalog endpoints: CRUD, GST rate config, and permission enforcement (catalog.read vs catalog.manage).
 */
@SpringBootTest(properties = {
    "app.security.bootstrap.super-admin.email=admin@test.local",
    "app.security.bootstrap.super-admin.password=SuperSecret-1234567890",
    "server.servlet.context-path="
})
@AutoConfigureMockMvc
@Testcontainers
class CatalogIntegrationTest {

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
    void servicesCanBeCreatedListedUpdatedAndDeactivated() throws Exception {
        String token = signInAndGetAccessToken();

        mockMvc.perform(get("/catalog/services").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());

        String createBody = "{\"code\":\"WEB-DEV\",\"name\":\"Web Development\","
            + "\"description\":\"Website build\",\"hsnSac\":\"998311\",\"taxClass\":\"TAXABLE\","
            + "\"defaultGstRate\":18.00,\"unit\":\"project\",\"defaultRate\":50000.00,\"taxInclusive\":false}";

        MvcResult created = mockMvc.perform(post("/catalog/services")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.hsnSac").value("998311"))
            .andReturn();

        long id = objectMapper.readTree(created.getResponse().getContentAsString())
            .path("data").path("id").asLong();
        assert id > 0;

        // Duplicate code -> 409.
        mockMvc.perform(post("/catalog/services")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isConflict());

        // Update rate and description.
        mockMvc.perform(patch("/catalog/services/" + id)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"WEB-DEV\",\"name\":\"Web Development\","
                    + "\"hsnSac\":\"998311\",\"taxClass\":\"TAXABLE\",\"defaultGstRate\":5.00,"
                    + "\"defaultRate\":45000.00}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.defaultGstRate").value(5.00));

        // Soft-delete.
        mockMvc.perform(delete("/catalog/services/" + id).header("Authorization", bearer(token)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/catalog/services/" + id).header("Authorization", bearer(token)))
            .andExpect(status().isNotFound());
    }

    @Test
    void gstRatesAreSeededAndValidated() throws Exception {
        String token = signInAndGetAccessToken();

        mockMvc.perform(get("/catalog/gst-rates").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(7))
            .andExpect(jsonPath("$.data[?(@.code=='GST_18')].rate").value(18.00));

        // effectiveTo before effectiveFrom -> 400.
        mockMvc.perform(post("/catalog/gst-rates")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"GST_45\",\"name\":\"45% slab\",\"rate\":45.00,"
                    + "\"effectiveFrom\":\"2026-03-01\",\"effectiveTo\":\"2026-01-01\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void viewerCanReadButCannotManageCatalog() throws Exception {
        String adminToken = signInAndGetAccessToken();
        mockMvc.perform(post("/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Viewer\",\"email\":\"viewer2@test.local\","
                    + "\"password\":\"ViewerPass-1234567890\",\"roles\":[\"VIEWER\"]}"))
            .andExpect(status().isOk());

        String viewerToken = signIn("viewer2@test.local", "ViewerPass-1234567890")
            .path("accessToken").asText();

        mockMvc.perform(get("/catalog/services").header("Authorization", bearer(viewerToken)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/catalog/services")
                .header("Authorization", bearer(viewerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"X\",\"name\":\"X\",\"hsnSac\":\"111\","
                    + "\"taxClass\":\"TAXABLE\",\"defaultGstRate\":18.00}"))
            .andExpect(status().isForbidden());
    }

    private JsonNode signIn(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String signInAndGetAccessToken() throws Exception {
        return signIn("admin@test.local", "SuperSecret-1234567890").path("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}