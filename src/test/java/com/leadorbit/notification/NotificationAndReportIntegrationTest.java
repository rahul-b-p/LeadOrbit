package com.leadorbit.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

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
import com.leadorbit.notification.domain.NotificationOutbox;
import com.leadorbit.notification.repository.NotificationOutboxRepository;
import com.leadorbit.notification.service.NotificationService;

@SpringBootTest(properties = {
    "app.security.bootstrap.super-admin.email=admin@test.local",
    "app.security.bootstrap.super-admin.password=SuperSecret-1234567890",
    "server.servlet.context-path=",
    "app.scheduling.enabled=false",
    "app.mail.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class NotificationAndReportIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NotificationService notificationService;

    @Autowired
    NotificationOutboxRepository outboxRepository;

    @Test
    void issuingAnInvoiceEnqueuesAndDispatchesAnEmailAndPdfIsDownloadable() throws Exception {
        String token = signInAndGetAccessToken();
        long sellerId = sellerProfile(token);
        long contactId = contact(token, "Notif Customer", "notif@test.local");

        long invoiceId = createAndIssueInvoice(token, sellerId, contactId, "Notif");

        // Outbox row written in the same transaction as the issue.
        JsonNode outbox = listOutbox(token);
        JsonNode row = findOutbox(outbox, "invoice-issued", "notif@test.local");
        assertThat(row).isNotNull();
        assertThat(row.path("status").asText()).isEqualTo("PENDING");
        assertThat(row.path("recipientEmail").asText()).isEqualTo("notif@test.local");
        assertThat(row.path("subject").asText()).contains("INV/");
        assertThat(row.path("body").asText()).contains("INV/").contains("LeadOrbit");

        // PDF endpoint streams bytes for issued invoices.
        MvcResult pdf = mockMvc.perform(get("/billing/invoices/" + invoiceId + "/pdf")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andReturn();
        byte[] bytes = pdf.getResponse().getContentAsByteArray();
        assertThat(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        // Manual dispatch (scheduling disabled in tests): simulated SMTP send.
        mockMvc.perform(post("/notifications/dispatch")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk());
        JsonNode after = findOutbox(listOutbox(token), "invoice-issued", "notif@test.local");
        assertThat(after.path("status").asText()).isEqualTo("SENT");
        assertThat(after.path("attempts").asInt()).isEqualTo(1);

        long outboxId = after.path("id").asLong();
        mockMvc.perform(get("/notifications/outbox/" + outboxId + "/deliveries")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].provider").value("SIMULATED"))
            .andExpect(jsonPath("$.data[0].status").value("SENT"));
    }

    @Test
    void duplicateIdempotencyKeyNeverCreatesASecondNotification() {
        String key = "payment-verified:420000-test";
        notificationService.enqueue("payment-verified", key, "payment-verified",
            "dup@test.local", java.util.Map.of("invoiceNumber", "INV/26-27/000001",
                "customerName", "Dup", "sellerName", "LeadOrbit", "amount", "500.00"));
        notificationService.enqueue("payment-verified", key, "payment-verified",
            "dup@test.local", java.util.Map.of("invoiceNumber", "INV/26-27/000001",
                "customerName", "Dup", "sellerName", "LeadOrbit", "amount", "500.00"));

        long count = outboxRepository.findAll().stream()
            .map(NotificationOutbox::getIdempotencyKey)
            .filter(key::equals)
            .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void reportsExposeRevenueAccountsReceivableAndMrr() throws Exception {
        String token = signInAndGetAccessToken();
        long sellerId = sellerProfile(token);
        long contactId = contact(token, "Report Customer", "report@test.local");

        // Invoice dated in a distinct past month, due 100 days ago: lands only in our
        // assertions (revenue month is unique; AR aging bucket 90+ is unique to this test).
        long invoiceId = createAndIssueInvoice(token, sellerId, contactId, "Report",
            "2026-06-10", java.time.LocalDate.now().minusDays(100).toString());

        mockMvc.perform(get("/reports/revenue-summary")
                .param("from", "2026-06-01").param("to", "2026-06-30")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].invoices").value(1))
            .andExpect(jsonPath("$.data[0].gross").value(11800.00))
            .andExpect(jsonPath("$.data[0].collected").value(0.00));

        mockMvc.perform(get("/reports/accounts-receivable")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[4].bucket").value("90+ Days"))
            .andExpect(jsonPath("$.data[4].due").value(11800.00));

        // MRR: a QUARTERLY 24000 plan -> 8000/month.
        long planId = plan(token, "PLAN-MRR-" + System.nanoTime(), "Report Plan",
            "24000.00", "QUARTERLY");
        subscription(token, planId, contactId);

        mockMvc.perform(get("/reports/mrr")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.activeSubscriptions").value(1))
            .andExpect(jsonPath("$.data.mrr").value(8000.00))
            .andExpect(jsonPath("$.data.annualRunRate").value(96000.00));
    }

    @Test
    void viewerWithoutDownloadOrNotificationPermissionsIsForbidden() throws Exception {
        String admin = signInAndGetAccessToken();
        mockMvc.perform(post("/users")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Plain Viewer\",\"email\":\"plainviewer@test.local\","
                    + "\"password\":\"ViewerPass-1234567890\",\"roles\":[\"VIEWER\"]}"))
            .andExpect(status().isOk());

        long sellerId = sellerProfile(admin);
        long contactId = contact(admin, "Viewer Invoice", "viewerinv@test.local");
        long invoiceId = createAndIssueInvoice(admin, sellerId, contactId, "Viewer");
        String viewer = signIn("plainviewer@test.local", "ViewerPass-1234567890")
            .path("accessToken").asText();

        mockMvc.perform(get("/billing/invoices/" + invoiceId + "/pdf")
                .header("Authorization", bearer(viewer)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/notifications/outbox")
                .header("Authorization", bearer(viewer)))
            .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ fixture

    private JsonNode listOutbox(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/notifications/outbox")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode findOutbox(JsonNode array, String eventType, String recipientEmail) {
        for (JsonNode node : array) {
            if (eventType.equals(node.path("eventType").asText())
                && recipientEmail.equals(node.path("recipientEmail").asText())) {
                return node;
            }
        }
        return null;
    }

    private long createAndIssueInvoice(String token, long sellerId, long contactId,
                                       String suffix) throws Exception {
        return createAndIssueInvoice(token, sellerId, contactId, suffix, null, null);
    }

    private long createAndIssueInvoice(String token, long sellerId, long contactId,
                                       String suffix, String invoiceDate, String dueDate)
        throws Exception {
        String dateJson = (invoiceDate != null
            ? ",\"invoiceDate\":\"" + invoiceDate + "\"" : "")
            + (dueDate != null ? ",\"dueDate\":\"" + dueDate + "\"" : "");
        long draft = idOf(mockMvc.perform(post("/billing/invoices")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sellerId\":" + sellerId
                    + ",\"contactId\":" + contactId
                    + ",\"placeOfSupplyState\":\"27\",\"currency\":\"INR\"" + dateJson
                    + ",\"lines\":[{\"description\":\"Retainer - " + suffix
                    + "\",\"hsnSac\":\"9983\",\"quantity\":1,\"unit\":\"month\","
                    + "\"rate\":10000.00,\"discount\":0,\"gstRate\":18}]}"))
            .andExpect(status().isOk()).andReturn(), "data.id");
        return idOf(mockMvc.perform(post("/billing/invoices/" + draft + "/issue")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ISSUED"))
            .andReturn(), "data.id");
    }

    private long contact(String token, String name, String email) throws Exception {
        return idOf(mockMvc.perform(post("/crm/contacts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\","
                    + "\"phone\":\"+919999999999\",\"designation\":\"Buyer\"}"))
            .andExpect(status().isOk()).andReturn(), "data.id");
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

    private long subscription(String token, long planId, long contactId) throws Exception {
        return idOf(mockMvc.perform(post("/subscriptions")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planId\":" + planId + ",\"contactId\":" + contactId
                    + ",\"startDate\":\"2026-09-01\"}"))
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