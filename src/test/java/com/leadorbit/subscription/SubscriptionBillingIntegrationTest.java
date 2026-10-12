package com.leadorbit.subscription;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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
import com.leadorbit.subscription.service.BillingJobService;

/**
 * Recurring billing: plans, subscriptions, the day job driven by next_billing_date, restart-safe
 * idempotency (unique run/period keys), period settlement when the linked invoice is paid, and
 * subscription state transitions.
 */
@SpringBootTest(properties = {
    "app.security.bootstrap.super-admin.email=admin@test.local",
    "app.security.bootstrap.super-admin.password=SuperSecret-1234567890",
    "server.servlet.context-path=",
    "app.scheduling.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class SubscriptionBillingIntegrationTest {

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
    private BillingJobService billingJobService;

    @Test
    void recurringInvoiceGeneratedOnceAndJobIsRestartSafe() throws Exception {
        String token = signInAndGetAccessToken();
        long sellerId = sellerProfile(token);
        long companyId = company(token, "SubCo Alpha", "27");
        long planId = plan(token, "ALPHA-120", "Support Plan", "12000.00", "MONTHLY");

        long subscriptionId = createSubscription(token, planId, companyId, "2026-01-01");

        // First run bills the due period (next_billing_date = start_date = 2026-01-01).
        String summary = billingJobService.run(LocalDate.of(2026, 1, 1));
        org.assertj.core.api.Assertions.assertThat(summary).startsWith("COMPLETED");

        mockMvc.perform(get("/subscriptions/" + subscriptionId + "/periods")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].periodStart").value("2026-01-01"))
            .andExpect(jsonPath("$.data[0].periodEnd").value("2026-01-31"))
            .andExpect(jsonPath("$.data[0].amount").value(12000.00))
            .andExpect(jsonPath("$.data[0].status").value("INVOICED"))
            .andExpect(jsonPath("$.data[0].invoiceId").isNumber());

        long invoiceId = idOf(mockMvc.perform(get("/subscriptions/" + subscriptionId + "/periods")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andReturn(), "data[0].invoiceId");

        // GST math on the recurring invoice: 12000 + 1080 CGST + 1080 SGST = 14160.
        mockMvc.perform(get("/billing/invoices/" + invoiceId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ISSUED"))
            .andExpect(jsonPath("$.data.invoiceNumber").value(matchesPattern("INV/\\d{4}-\\d{2}/\\d{6}")))
            .andExpect(jsonPath("$.data.taxableAmount").value(12000.00))
            .andExpect(jsonPath("$.data.cgstAmount").value(1080.00))
            .andExpect(jsonPath("$.data.sgstAmount").value(1080.00))
            .andExpect(jsonPath("$.data.grandTotal").value(14160.00))
            .andExpect(jsonPath("$.data.lines[0].description").value(
                containsString("Support Plan")));

        mockMvc.perform(get("/subscriptions/" + subscriptionId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nextBillingDate").value("2026-02-01"));

        // Re-running the same day must not double-invoice (unique run + period keys).
        String rerun = billingJobService.run(LocalDate.of(2026, 1, 1));
        org.assertj.core.api.Assertions.assertThat(rerun).isEqualTo("ALREADY_COMPLETED");
        mockMvc.perform(get("/subscriptions/" + subscriptionId + "/periods")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void periodSettlesToPaidWhenLinkedInvoiceIsPaid() throws Exception {
        String token = signInAndGetAccessToken();
        long sellerId = sellerProfile(token);
        long companyId = company(token, "SubCo Beta", "27");
        long planId = plan(token, "BETA-150", "Retainer Plan", "15000.00", "MONTHLY");
        long subscriptionId = createSubscription(token, planId, companyId, "2026-02-01");

        billingJobService.run(LocalDate.of(2026, 2, 1));

        long invoiceId = idOf(mockMvc.perform(get("/subscriptions/" + subscriptionId + "/periods")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andReturn(), "data[0].invoiceId");

        // Pay the full invoice (15000 + 18% = 17700): the period follows to PAID.
        mockMvc.perform(post("/payments/record")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invoiceId\":" + invoiceId + ",\"amount\":17700.00,"
                    + "\"method\":\"NEFT\",\"referenceNo\":\"BETA-NEFT\",\"paidOn\":\"2026-02-05\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("VERIFIED"));

        mockMvc.perform(get("/subscriptions/" + subscriptionId + "/periods")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].status").value("PAID"));
    }

    @Test
    void subscriptionStateTransitionsAndPlanChange() throws Exception {
        String token = signInAndGetAccessToken();
        long sellerId = sellerProfile(token);
        long companyId = company(token, "SubCo Gamma", "09");
        long planId = plan(token, "GAMMA-050", "Starter Plan", "5000.00", "MONTHLY");
        long planTwoId = plan(token, "GAMMA-080", "Growth Plan", "8000.00", "QUARTERLY");
        long subscriptionId = createSubscription(token, planId, companyId, "2026-03-01");

        mockMvc.perform(patch("/subscriptions/" + subscriptionId + "/pause")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PAUSED"));
        mockMvc.perform(patch("/subscriptions/" + subscriptionId + "/pause")
                .header("Authorization", bearer(token)))
            .andExpect(status().isConflict());

        mockMvc.perform(patch("/subscriptions/" + subscriptionId + "/resume")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/subscriptions/" + subscriptionId + "/change-plan")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planId\":" + planTwoId + ",\"prorated\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.planCode").value("GAMMA-080"))
            .andExpect(jsonPath("$.data.billingInterval").value("QUARTERLY"));

        // Proration is documented as not implemented -> rejected explicitly.
        mockMvc.perform(post("/subscriptions/" + subscriptionId + "/change-plan")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planId\":" + planTwoId + ",\"prorated\":true}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/subscriptions/" + subscriptionId + "/cancel")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mockMvc.perform(patch("/subscriptions/" + subscriptionId + "/cancel")
                .header("Authorization", bearer(token)))
            .andExpect(status().isConflict());
    }

    @Test
    void viewerCanReadButNotManageSubscriptions() throws Exception {
        String admin = signInAndGetAccessToken();
        mockMvc.perform(post("/users")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sub Viewer\",\"email\":\"subviewer@test.local\","
                    + "\"password\":\"ViewerPass-1234567890\",\"roles\":[\"VIEWER\"]}"))
            .andExpect(status().isOk());
        String viewer = signIn("subviewer@test.local", "ViewerPass-1234567890")
            .path("accessToken").asText();

        mockMvc.perform(get("/subscriptions").header("Authorization", bearer(viewer)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/subscriptions")
                .header("Authorization", bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planId\":1,\"companyId\":1,\"startDate\":\"2026-04-01\"}"))
            .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ fixture

    private long createSubscription(String token, long planId, long companyId,
                                    String startDate) throws Exception {
        return idOf(mockMvc.perform(post("/subscriptions")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planId\":" + planId + ",\"companyId\":" + companyId
                    + ",\"startDate\":\"" + startDate + "\",\"anchorDay\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.nextBillingDate").value(startDate))
            .andReturn(), "data.id");
    }

    private long plan(String token, String code, String name, String amount,
                      String interval) throws Exception {
        return idOf(mockMvc.perform(post("/subscriptions/plans")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\","
                    + "\"amount\":" + amount + ",\"billingInterval\":\"" + interval + "\"}"))
            .andExpect(status().isOk()).andReturn(), "data.id");
    }

    private long company(String token, String name, String stateCode) throws Exception {
        return idOf(mockMvc.perform(post("/crm/companies")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"stateCode\":\"" + stateCode + "\","
                    + "\"city\":\"Pune\",\"state\":\"Maharashtra\",\"country\":\"IN\"}"))
            .andExpect(status().isOk()).andReturn(), "data.id");
    }

    private long sellerProfile(String token) throws Exception {
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
            .andExpect(status().isOk()).andReturn(), "data.id");
    }

    private long idOf(MvcResult result, String expression) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        for (String part : expression.split("\\.")) {
            if (part.contains("[")) {
                String name = part.substring(0, part.indexOf('['));
                int index = Integer.parseInt(part.substring(part.indexOf('[') + 1, part.indexOf(']')));
                node = node.path(name).path(index);
            } else {
                node = node.path(part);
            }
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