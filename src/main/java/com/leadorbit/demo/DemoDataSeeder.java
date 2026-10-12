package com.leadorbit.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.billing.dto.InvoiceCreateRequest;
import com.leadorbit.billing.dto.InvoiceLineRequest;
import com.leadorbit.billing.dto.SellerProfileRequest;
import com.leadorbit.billing.repository.SellerProfileRepository;
import com.leadorbit.billing.service.InvoiceService;
import com.leadorbit.billing.service.SellerProfileService;
import com.leadorbit.catalog.domain.CatalogService;
import com.leadorbit.catalog.repository.CatalogServiceRepository;
import com.leadorbit.crm.domain.Contact;
import com.leadorbit.crm.repository.ContactRepository;
import com.leadorbit.subscription.domain.Plan;
import com.leadorbit.subscription.dto.PlanRequest;
import com.leadorbit.subscription.dto.SubscriptionRequest;
import com.leadorbit.subscription.repository.PlanRepository;
import com.leadorbit.subscription.service.PlanService;
import com.leadorbit.subscription.service.SubscriptionService;

/**
 * Idempotent demo dataset, enabled with {@code app.demo.enabled=true}. Every step is guarded by
 * an existence check so repeated startups are harmless. Fixtures use clearly-synthetic data.
 */
@Component
@ConditionalOnProperty(prefix = "app.demo", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String DEMO_EMAIL = "demo.customer@example.local";

    private final SellerProfileService sellerProfileService;
    private final SellerProfileRepository sellerProfileRepository;
    private final InvoiceService invoiceService;
    private final PlanService planService;
    private final SubscriptionService subscriptionService;
    private final PlanRepository planRepository;
    private final CatalogServiceRepository serviceRepository;
    private final ContactRepository contactRepository;

    public DemoDataSeeder(SellerProfileService sellerProfileService,
                          SellerProfileRepository sellerProfileRepository,
                          InvoiceService invoiceService,
                          PlanService planService,
                          SubscriptionService subscriptionService,
                          PlanRepository planRepository,
                          CatalogServiceRepository serviceRepository,
                          ContactRepository contactRepository) {
        this.sellerProfileService = sellerProfileService;
        this.sellerProfileRepository = sellerProfileRepository;
        this.invoiceService = invoiceService;
        this.planService = planService;
        this.subscriptionService = subscriptionService;
        this.planRepository = planRepository;
        this.serviceRepository = serviceRepository;
        this.contactRepository = contactRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Seeding demo data (app.demo.enabled=true)");
        try {
            seed();
            log.info("Demo data ready");
        } catch (Exception ex) {
            log.warn("Demo data seeding skipped after partial failure: {}", ex.getMessage());
        }
    }

    private void seed() {
        if (sellerProfileRepository.count() == 0) {
            sellerProfileService.create(new SellerProfileRequest(
                "LeadOrbit Demo Pvt Ltd", "LeadOrbit", "27AABPD0001D1ZM", "27",
                "401, Demo Tower, Mumbai, Maharashtra 400001", null, true, true));
        }

        CatalogService service;
        if (serviceRepository.existsByCodeAndDeletedAtIsNull("DEMO-RETAINER")) {
            service = serviceRepository.findByCodeAndDeletedAtIsNull("DEMO-RETAINER").orElseThrow();
        } else {
            service = new CatalogService();
            service.setCode("DEMO-RETAINER");
            service.setName("Demo Retainer (GST demo line)");
            service.setDescription("Synthetic catalogue line for demonstration only");
            service.setHsnSac("9983");
            service.setDefaultGstRate(new BigDecimal("18.00"));
            service.setUnit("month");
            service.setDefaultRate(new BigDecimal("10000.00"));
            service.setActive(true);
            serviceRepository.save(service);
        }

        Contact contact;
        if (contactRepository.existsByEmailAndDeletedAtIsNull(DEMO_EMAIL)) {
            contact = contactRepository.findAll().stream()
                .filter(c -> DEMO_EMAIL.equalsIgnoreCase(c.getEmail()))
                .filter(c -> c.getDeletedAt() == null)
                .findFirst().orElseThrow();
        } else {
            contact = new Contact();
            contact.setName("Demo Customer");
            contact.setEmail(DEMO_EMAIL);
            contact.setPhone("+91 90000 00000");
            contact.setDesignation("Procurement Lead");
            contact = contactRepository.save(contact);
        }

        if (planRepository.findByCodeAndDeletedAtIsNull("DEMO-MONTHLY").isEmpty()) {
            planService.create(new PlanRequest("DEMO-MONTHLY", "Demo Monthly Retainer",
                service.getId(), new BigDecimal("10000.00"), "MONTHLY", false,
                "Synthetic monthly retainer for demonstration", true));
        }
        Plan plan = planRepository.findByCodeAndDeletedAtIsNull("DEMO-MONTHLY").orElseThrow();
        Long contactId = contact.getId();

        if (subscriptionService.list(null).stream()
            .noneMatch(s -> s.contactId() != null && s.contactId().equals(contactId))) {
            subscriptionService.create(new SubscriptionRequest(
                plan.getId(), null, contact.getId(), LocalDate.now().minusMonths(1),
                null, null, null, null));
        }

        // One issued invoice to light up revenue, AR and the invoice-issued outbox template.
        // (Payments require an authenticated actor, so they can't be seeded at boot.)
        if (invoiceService.list(null).isEmpty()) {
            long sellerId = sellerProfileService.getDefault().id();
            var draft = invoiceService.createDraft(new InvoiceCreateRequest(
                "INV", sellerId, null, contact.getId(), null,
                LocalDate.now().minusDays(10), LocalDate.now().minusDays(5), "27",
                "INR", false,
                List.of(new InvoiceLineRequest(service.getId(),
                    "Demo retainer invoice line", "9983", BigDecimal.ONE, "month",
                    new BigDecimal("10000.00"), BigDecimal.ZERO, new BigDecimal("18.00")))));
            invoiceService.issue(draft.id());
        }
    }
}