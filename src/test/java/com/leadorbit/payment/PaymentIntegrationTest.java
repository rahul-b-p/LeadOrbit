package com.leadorbit.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
 * Manual payment lifecycle: submission does not allocate, verification does (under the row
 * lock), overpayment/rejection/duplicate guards, proof storage, and the preserved lead→client
 * conversion rule once cumulative VERIFIED payments exceed the lead's advance.
 */
@SpringBootTest(properties = {
    "app.security.bootstrap.super-admin.email=admin@test.local",
    "app.security.bootstrap.super-admin.password=SuperSecret-1234567890",
    "server.servlet.context-path=",
    "app.scheduling.enabled=false",
    "app.storage.local.base-path=./target/test-uploads"
})
@AutoConfigureMockMvc
@Testcontainers
class PaymentIntegrationTest {

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
    void submittedPaymentDoesNotAllocateUntilVerified() throws Exception {
        String token = signInAndGetAccessToken();
        InvoiceFixture fixture = newInvoice(token, "Acme Payments", "27", 0);

        long submitted = idOf(mockMvc.perform(post("/payments/submit")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invoiceId\":" + fixture.invoiceId() + ",\"amount\":5000.00,"
                    + "\"method\":\"NEFT\",\"referenceNo\":\"NEFT-1001\",\"paidOn\":\"2026-10-01\","
                    + "\"idempotencyKey\":\"pay-submit-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
            .andReturn(), "data.id");

        // Submission alone must not touch the invoice balance (only VERIFIED payments allocate).
        mockMvc.perform(get("/billing/invoices/" + fixture.invoiceId())
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ISSUED"))
            .andExpect(jsonPath("$.data.amountPaid").value(0.00));

        mockMvc.perform(patch("/payments/" + submitted + "/verify")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"VERIFY\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("VERIFIED"))
            .andExpect(jsonPath("$.data.verifiedBy").isNotEmpty());

        mockMvc.perform(get("/billing/invoices/" + fixture.invoiceId())
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PARTIALLY_PAID"))
            .andExpect(jsonPath("$.data.amountPaid").value(5000.00))
            .andExpect(jsonPath("$.data.outstanding").value(6800.00));

        // Second decision on the same payment is rejected (S4: idempotent financial ops).
        mockMvc.perform(patch("/payments/" + submitted + "/verify")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"VERIFY\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void overpaymentAndRejectionRulesAreEnforced() throws Exception {
        String token = signInAndGetAccessToken();
        InvoiceFixture fixture = newInvoice(token, "Acme Overpay", "27", 0);

        // Overpayment rejected at verification time (unless credit-balance policy).
        long over = idOf(mockMvc.perform(post("/payments/submit")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invoiceId\":" + fixture.invoiceId() + ",\"amount\":20000.00,"
                    + "\"method\":\"UPI\",\"paidOn\":\"2026-10-01\"}"))
            .andExpect(status().isOk()).andReturn(), "data.id");
        mockMvc.perform(patch("/payments/" + over + "/verify")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"VERIFY\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("exceeds outstanding")));

        // Rejecting needs a reason; invoice balance stays untouched.
        long rejectMe = idOf(mockMvc.perform(post("/payments/submit")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invoiceId\":" + fixture.invoiceId() + ",\"amount\":1000.00,"
                    + "\"method\":\"CASH\",\"paidOn\":\"2026-10-01\"}"))
            .andExpect(status().isOk()).andReturn(), "data.id");
        mockMvc.perform(patch("/payments/" + rejectMe + "/verify")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/payments/" + rejectMe + "/verify")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\",\"reason\":\"Customer disputed the charge\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"));

        mockMvc.perform(get("/billing/invoices/" + fixture.invoiceId())
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.amountPaid").value(0.00));
    }

    @Test
    void duplicateGuardsRejectRepeatedFinancialEntries() throws Exception {
        String token = signInAndGetAccessToken();
        InvoiceFixture fixture = newInvoice(token, "Acme Duplicate", "27", 0);

        String body = "{\"invoiceId\":" + fixture.invoiceId() + ",\"amount\":500.00,"
            + "\"method\":\"UPI\",\"referenceNo\":\"REF-99\",\"paidOn\":\"2026-10-01\","
            + "\"idempotencyKey\":\"dup-key-1\"}";
        mockMvc.perform(post("/payments/submit")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());

        // Same idempotency key -> 409.
        mockMvc.perform(post("/payments/submit")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict());

        // Same (invoice_id, reference_no) with a different key -> 409 via unique index.
        mockMvc.perform(post("/payments/submit")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invoiceId\":" + fixture.invoiceId() + ",\"amount\":500.00,"
                    + "\"method\":\"UPI\",\"referenceNo\":\"REF-99\",\"paidOn\":\"2026-10-01\","
                    + "\"idempotencyKey\":\"dup-key-2\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void proofUploadIsStoredAndDownloadableWithContentIntegrity() throws Exception {
        String token = signInAndGetAccessToken();
        InvoiceFixture fixture = newInvoice(token, "Acme Proofs", "27", 0);

        long paymentId = idOf(mockMvc.perform(post("/payments/record")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invoiceId\":" + fixture.invoiceId() + ",\"amount\":11800.00,"
                    + "\"method\":\"RTGS\",\"referenceNo\":\"RTGS-77\",\"paidOn\":\"2026-10-01\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("VERIFIED"))
            .andReturn(), "data.id");

        byte[] pngBytes = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
        long proofId = idOf(mockMvc.perform(multipart("/payments/" + paymentId + "/proofs")
                .file(new MockMultipartFile("file", "screenshot.png", "image/png", pngBytes))
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sha256").value(
                "66840dda154e8a113c31dd0ad32f7f3a366a80e8136979d8f5a101d3d29d6f72"))
            .andReturn(), "data.id");

        mockMvc.perform(get("/payments/proofs/" + proofId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(content().bytes(pngBytes));
    }

    @Test
    void leadConvertsToClientOncePaymentsExceedAdvance() throws Exception {
        String token = signInAndGetAccessToken();
        // Lead with a 5000 advance; a 6000 verified payment crosses the threshold.
        InvoiceFixture fixture = newInvoice(token, "Acme Converted", "27", 5000);

        long paymentId = idOf(mockMvc.perform(post("/payments/record")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invoiceId\":" + fixture.invoiceId() + ",\"amount\":6000.00,"
                    + "\"method\":\"CHEQUE\",\"paidOn\":\"2026-10-01\"}"))
            .andExpect(status().isOk()).andReturn(), "data.id");

        mockMvc.perform(get("/crm/leads/" + fixture.leadId())
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("WON"))
            .andExpect(jsonPath("$.data.convertedAt").isNotEmpty());
    }

    @Test
    void verifyRequiresPaymentVerifyPermission() throws Exception {
        String admin = signInAndGetAccessToken();
        InvoiceFixture fixture = newInvoice(admin, "Acme Rbac", "27", 0);

        long paymentId = idOf(mockMvc.perform(post("/payments/submit")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invoiceId\":" + fixture.invoiceId() + ",\"amount\":500.00,"
                    + "\"method\":\"CASH\",\"paidOn\":\"2026-10-01\"}"))
            .andExpect(status().isOk()).andReturn(), "data.id");

        mockMvc.perform(post("/users")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Payment Viewer\",\"email\":\"payviewer@test.local\","
                    + "\"password\":\"ViewerPass-1234567890\",\"roles\":[\"VIEWER\"]}"))
            .andExpect(status().isOk());
        String viewer = signIn("payviewer@test.local", "ViewerPass-1234567890")
            .path("accessToken").asText();

        mockMvc.perform(get("/payments?invoiceId=" + fixture.invoiceId())
                .header("Authorization", bearer(viewer)))
            .andExpect(status().isOk());
        mockMvc.perform(patch("/payments/" + paymentId + "/verify")
                .header("Authorization", bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"VERIFY\"}"))
            .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ fixture

    /**
     * Creates a company, an (optional-advance) lead and an issued GST invoice for it
     * (18% intra-state: 10000 + 900 CGST + 900 SGST = 11800).
     */
    private InvoiceFixture newInvoice(String token, String companyName, String stateCode,
                                      int advanceAmount) throws Exception {
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
                    + "\"email\":\"" + companyName.replace(" ", "") + "@test.local\"}"))
            .andExpect(status().isOk()).andReturn(), "data.id");

        long leadId = idOf(mockMvc.perform(post("/crm/leads")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Opportunity\",\"companyId\":" + companyId
                    + ",\"contactId\":" + contactId + ",\"stageCode\":\"QUALIFIED\","
                    + "\"value\":11800,\"advanceAmount\":" + advanceAmount + "}"))
            .andExpect(status().isOk()).andReturn(), "data.id");

        long sellerId = sellerProfile(token);

        long invoiceId = idOf(mockMvc.perform(post("/billing/invoices")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sellerId\":" + sellerId + ",\"leadId\":" + leadId + ","
                    + "\"lines\":[{\"description\":\"Consulting\",\"hsnSac\":\"9983\","
                    + "\"quantity\":1,\"rate\":10000.00}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.grandTotal").value(11800.00)).andReturn(), "data.id");

        mockMvc.perform(post("/billing/invoices/" + invoiceId + "/issue")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ISSUED"));
        return new InvoiceFixture(invoiceId, leadId);
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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isDefault").value(true)).andReturn(), "data.id");
    }

    private record InvoiceFixture(long invoiceId, long leadId) {
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