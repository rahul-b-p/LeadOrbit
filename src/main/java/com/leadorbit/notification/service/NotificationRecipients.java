package com.leadorbit.notification.service;

import com.leadorbit.billing.domain.Invoice;
import com.leadorbit.crm.domain.Contact;

/**
 * Resolves the notification recipient for an invoice: the invoice contact first (or its
 * implicit customer), else the lead's contact. Company-only invoices resolve to the company's
 * primary contact if one exists, else {@code null} (no notification).
 */
public final class NotificationRecipients {

    private NotificationRecipients() {
    }

    public static String emailOf(Invoice invoice) {
        Contact contact = contactOf(invoice);
        if (contact != null && contact.getEmail() != null && !contact.getEmail().isBlank()) {
            return contact.getEmail().trim();
        }
        return null;
    }

    public static String nameOf(Invoice invoice) {
        if (invoice.getContact() != null) {
            return invoice.getContact().getName();
        }
        if (invoice.getCompany() != null) {
            return invoice.getCompany().getName();
        }
        if (invoice.getLead() != null) {
            if (invoice.getLead().getContact() != null) {
                return invoice.getLead().getContact().getName();
            }
            if (invoice.getLead().getCompany() != null) {
                return invoice.getLead().getCompany().getName();
            }
            return invoice.getLead().getTitle();
        }
        return "Customer";
    }

    private static Contact contactOf(Invoice invoice) {
        if (invoice.getContact() != null) {
            return invoice.getContact();
        }
        if (invoice.getLead() != null) {
            return invoice.getLead().getContact();
        }
        return null;
    }
}