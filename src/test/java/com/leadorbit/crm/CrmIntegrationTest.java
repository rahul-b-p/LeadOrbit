package com.leadorbit.crm;

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
 * CRM flow: company -> contact -> lead -> activities -> conversion, plus pipeline gating.
 */
@SpringBootTest(properties = {
    "app.security.bootstrap.super-admin.email=admin@test.local",
    "app.security.bootstrap.super-admin.password=SuperSecret-1234567890",
    "server.servlet.context-path="
})
@AutoConfigureMockMvc
@Testcontainers
class CrmIntegrationTest {

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
    void leadLifecycleCompanyContactActivitiesAndConversion() throws Exception {
        String token = signInAndGetAccessToken();

        // Seeded pipeline stages and sources are visible.
        mockMvc.perform(get("/crm/pipeline-stages").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(6));
        mockMvc.perform(get("/crm/lead-sources").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(6));

        // Company
        long companyId = idOf(mockMvc.perform(post("/crm/companies")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Acme Widgets\",\"gstin\":\"27AAPFU0939F1ZV\",\"stateCode\":\"27\","
                + "\"city\":\"Pune\",\"state\":\"Maharashtra\",\"country\":\"IN\"}"))
            .andExpect(status().isOk()).andReturn(), "data.id");

        // Contact under company with marketing opt-in
        long contactId = idOf(mockMvc.perform(post("/crm/contacts")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"companyId\":" + companyId + ",\"name\":\"Priya Sharma\","
                + "\"email\":\"priya@acme.example\",\"designation\":\"Procurement\",\"marketingOptIn\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.marketingOptIn").value(true)).andReturn(), "data.id");

        // Lead referencing company + contact at QUALIFIED stage
        long leadId = idOf(mockMvc.perform(post("/crm/leads")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Website redesign\",\"companyId\":" + companyId
                + ",\"contactId\":" + contactId + ",\"sourceId\":null,\"stageCode\":\"QUALIFIED\","
                + "\"value\":250000,\"advanceAmount\":50000,\"notes\":\"Portal + CMS\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("OPEN"))
            .andExpect(jsonPath("$.data.invoiceable").value(true)).andReturn(), "data.id");

        // Lead without any company/contact -> 400
        mockMvc.perform(post("/crm/leads")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Orphan lead\",\"stageCode\":\"NEW\"}"))
            .andExpect(status().isBadRequest());

        // Unknown stage -> 400
        mockMvc.perform(post("/crm/leads")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Bad stage\",\"companyId\":" + companyId
                    + ",\"stageCode\":\"NOPE\"}"))
            .andExpect(status().isBadRequest());

        // Activities on lead timeline
        mockMvc.perform(post("/crm/leads/" + leadId + "/activities")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CALL\",\"subject\":\"Discovery call\",\"body\":\"Requirements gathered\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.type").value("CALL"));

        mockMvc.perform(get("/crm/leads/" + leadId + "/activities")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1));

        // Move to WON via stage change -> status derives to WON
        mockMvc.perform(patch("/crm/leads/" + leadId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Website redesign\",\"companyId\":" + companyId
                    + ",\"stageCode\":\"WON\",\"value\":250000,\"advanceAmount\":50000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("WON"))
            .andExpect(jsonPath("$.data.invoiceable").value(false));

        // Filter by status
        mockMvc.perform(get("/crm/leads?status=WON").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1));

        // Re-open the lead (PROPOSAL_SENT), then convert explicitly
        mockMvc.perform(patch("/crm/leads/" + leadId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Website redesign\",\"companyId\":" + companyId
                    + ",\"stageCode\":\"PROPOSAL_SENT\",\"value\":250000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("OPEN"));

        mockMvc.perform(post("/crm/leads/" + leadId + "/convert")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("WON"))
            .andExpect(jsonPath("$.data.convertedContactId").value(contactId));

        // Converting twice -> 409
        mockMvc.perform(post("/crm/leads/" + leadId + "/convert")
                .header("Authorization", bearer(token)))
            .andExpect(status().isConflict());
    }

    @Test
    void viewerCannotManageCrm() throws Exception {
        String admin = signInAndGetAccessToken();
        mockMvc.perform(post("/users")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Viewer\",\"email\":\"viewer3@test.local\","
                    + "\"password\":\"ViewerPass-1234567890\",\"roles\":[\"VIEWER\"]}"))
            .andExpect(status().isOk());

        String viewer = signIn("viewer3@test.local", "ViewerPass-1234567890")
            .path("accessToken").asText();

        mockMvc.perform(get("/crm/leads").header("Authorization", bearer(viewer)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/crm/leads")
                .header("Authorization", bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"stageCode\":\"NEW\"}"))
            .andExpect(status().isForbidden());
    }

    private long idOf(MvcResult result, String expression) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        for (String part : expression.split("\\.")) {
            node = node.path(part);
        }
        return node.asLong();
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