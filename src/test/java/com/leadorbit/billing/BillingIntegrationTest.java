package com.leadorbit.billing;

import static org.hamcrest.Matchers.matchesPattern;
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
 * GST invoicing flow: seller profile, draft creation with CGST/SGST vs IGST,
 * gapless FY numbering on issue, immutability after issue, cancellation, and
 * the lead-XOR-customer + invoiceable-lead rules.
 */
@SpringBootTest(properties = {
    "app.security.bootstrap.super-admin.email=admin@test.local",
    "app.security.bootstrap.super-admin.password=SuperSecret-1234567890",
    "server.servlet.context-path="
})
@AutoConfigureMockMvc
@Testcontainers
class BillingIntegrationTest {

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
    void invoiceLifecycleWithGstSplittingAndImmutability() throws Exception {
        String token = signInAndGetAccessToken();

        long sellerId = createSellerProfile(token);

        // Customer in the same state (27) as the seller -> CGST+SGST.
        long leadId = createCompanyAndLead(token, "27", "Acme Intra");

        String draftBody = "{\"sellerId\":" + sellerId + ",\"leadId\":" + leadId + ","
            + "\"lines\":[{\"description\":\"Website redesign\",\"hsnSac\":\"998311\","
            + "\"quantity\":1,\"unit\":\"project\",\"rate\":45000.00}]}";

        // Client-supplied amountPaid must be ignored (server-owned monetary field).
        String tampered = draftBody.replace("}]}", ",\"amountPaid\":99999}]}");

        long invoiceId = idOf(mockMvc.perform(post("/billing/invoices")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(tampered))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.taxableAmount").value(45000.00))
            .andExpect(jsonPath("$.data.cgstAmount").value(4050.00))
            .andExpect(jsonPath("$.data.sgstAmount").value(4050.00))
            .andExpect(jsonPath("$.data.igstAmount").value(0.00))
            .andExpect(jsonPath("$.data.grandTotal").value(53100.00))
            .andExpect(jsonPath("$.data.amountPaid").value(0.00))
            .andReturn(), "data.id");

        // Issue -> gapless FY number, snapshots taken, immutable from here on.
        mockMvc.perform(post("/billing/invoices/" + invoiceId + "/issue")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ISSUED"))
            .andExpect(jsonPath("$.data.issueDate").isNotEmpty())
            .andExpect(jsonPath("$.data.invoiceNumber").value(matchesPattern("INV/\\d{4}-\\d{2}/\\d{6}")))
            .andExpect(jsonPath("$.data.dueDate").isNotEmpty());

        // Re-issue and delete must both be rejected -> S2 (immutable once issued).
        mockMvc.perform(post("/billing/invoices/" + invoiceId + "/issue")
                .header("Authorization", bearer(token)))
            .andExpect(status().isConflict());
        mockMvc.perform(delete("/billing/invoices/" + invoiceId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isBadRequest());

        // Event trail shows create + issue.
        mockMvc.perform(get("/billing/invoices/" + invoiceId + "/events")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.eventType=='INVOICE_ISSUED')]").exists());

        // Cancel -> CANCELLED; repeat cancel -> 409.
        mockMvc.perform(post("/billing/invoices/" + invoiceId + "/cancel")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Duplicate order\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mockMvc.perform(post("/billing/invoices/" + invoiceId + "/cancel")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"again\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void interStateInvoiceChargesIgstAndRulesAreEnforced() throws Exception {
        String token = signInAndGetAccessToken();
        long sellerId = createSellerProfile(token);

        // Customer company in UP (09) while seller is in MH (27) -> IGST.
        long companyId = idOf(mockMvc.perform(post("/crm/companies")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lucknow Traders\",\"stateCode\":\"09\",\"gstin\":\"09ABCDE1234F1Z5\","
                    + "\"city\":\"Lucknow\",\"state\":\"Uttar Pradesh\",\"country\":\"IN\"}"))
            .andExpect(status().isOk()).andReturn(), "data.id");

        mockMvc.perform(post("/billing/invoices")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sellerId\":" + sellerId + ",\"companyId\":" + companyId + ","
                    + "\"lines\":[{\"description\":\"Support retainers\",\"hsnSac\":\"9983\","
                    + "\"quantity\":2,\"rate\":10000.00}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.placeOfSupplyState").value("09"))
            .andExpect(jsonPath("$.data.igstAmount").value(3600.00))
            .andExpect(jsonPath("$.data.cgstAmount").value(0.00))
            .andExpect(jsonPath("$.data.sgstAmount").value(0.00));

        // Lead XOR customer.
        mockMvc.perform(post("/billing/invoices")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sellerId\":" + sellerId + ",\"companyId\":" + companyId + ",\"leadId\":999999,"
                    + "\"lines\":[{\"description\":\"x\",\"hsnSac\":\"9983\",\"quantity\":1,\"rate\":10}]}"))
            .andExpect(status().isBadRequest());

        // Non-invoiceable lead (LOST stage) -> 400.
        long lostLead = createCompanyAndLead(token, "27", "Lost Deals Co");
        mockMvc.perform(patch("/crm/leads/" + lostLead)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Lost deal\",\"stageCode\":\"LOST\",\"value\":1000}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/billing/invoices")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sellerId\":" + sellerId + ",\"leadId\":" + lostLead + ","
                    + "\"lines\":[{\"description\":\"x\",\"hsnSac\":\"9983\",\"quantity\":1,\"rate\":10}]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void viewerCannotCreateInvoices() throws Exception {
        String admin = signInAndGetAccessToken();
        mockMvc.perform(post("/users")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Viewer\",\"email\":\"viewer4@test.local\","
                    + "\"password\":\"ViewerPass-1234567890\",\"roles\":[\"VIEWER\"]}"))
            .andExpect(status().isOk());

        String viewer = signIn("viewer4@test.local", "ViewerPass-1234567890")
            .path("accessToken").asText();

        mockMvc.perform(get("/billing/invoices").header("Authorization", bearer(viewer)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/billing/invoices")
                .header("Authorization", bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"description\":\"x\",\"hsnSac\":\"9983\",\"quantity\":1,\"rate\":10}]}"))
            .andExpect(status().isForbidden());
    }

    private long createSellerProfile(String token) throws Exception {
        // Idempotent: the DB is shared across tests within this class.
        MvcResult existing = mockMvc.perform(get("/billing/seller-profiles")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andReturn();
        JsonNode list = objectMapper.readTree(existing.getResponse().getContentAsString()).path("data");
        if (list.isArray() && list.size() > 0) {
            return list.get(0).path("id").asLong();
        }
        return idOf(mockMvc.perform(post("/billing/seller-profiles")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"legalName\":\"LeadOrbit Software Pvt Ltd\",\"tradeName\":\"LeadOrbit\","
                    + "\"gstin\":\"27AABCS1429B1ZM\",\"stateCode\":\"27\","
                    + "\"registeredAddress\":\"MG Road, Pune 411001\",\"isDefault\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isDefault").value(true)).andReturn(), "data.id");
    }

    private long createCompanyAndLead(String token, String stateCode, String companyName)
        throws Exception {
        long companyId = idOf(mockMvc.perform(post("/crm/companies")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + companyName + "\",\"stateCode\":\"" + stateCode + "\","
                    + "\"city\":\"Pune\",\"state\":\"Maharashtra\",\"country\":\"IN\"}"))
            .andExpect(status().isOk()).andReturn(), "data.id");

        long contactId = idOf(mockMvc.perform(post("/crm/contacts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyId\":" + companyId + ",\"name\":\"Alice\","
                    + "\"email\":\"alice-" + companyName.replace(" ", "") + "@test.local\"}"))
            .andExpect(status().isOk()).andReturn(), "data.id");

        return idOf(mockMvc.perform(post("/crm/leads")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Sales opportunity\",\"companyId\":" + companyId
                    + ",\"contactId\":" + contactId + ",\"stageCode\":\"QUALIFIED\",\"value\":45000}"))
            .andExpect(status().isOk()).andReturn(), "data.id");
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