package com.leadorbit.security;

/** Central catalogue of permission codes referenced from authorization annotations. */
public final class PermissionCodes {

    private PermissionCodes() {
    }

    public static final String USER_READ = "user.read";
    public static final String USER_MANAGE = "user.manage";
    public static final String USER_MANAGE_PERMISSIONS = "user.manage_permissions";
    public static final String ROLE_MANAGE = "role.manage";
    public static final String AUDIT_READ = "audit.read";
    public static final String CATALOG_READ = "catalog.read";
    public static final String CATALOG_MANAGE = "catalog.manage";
    public static final String CRM_READ = "crm.read";
    public static final String CRM_MANAGE = "crm.manage";
    public static final String INVOICE_READ = "invoice.read";
    public static final String INVOICE_CREATE = "invoice.create";
    public static final String INVOICE_ISSUE = "invoice.issue";
    public static final String INVOICE_CANCEL = "invoice.cancel";
    public static final String INVOICE_DOWNLOAD = "invoice.download";
    public static final String PAYMENT_READ = "payment.read";
    public static final String PAYMENT_RECORD = "payment.record";
    public static final String PAYMENT_VERIFY = "payment.verify";
    public static final String SUBSCRIPTION_READ = "subscription.read";
    public static final String SUBSCRIPTION_MANAGE = "subscription.manage";
    public static final String NOTIFICATION_MANAGE = "notification.manage";
    public static final String REPORT_READ = "report.read";
    public static final String REPORT_EXPORT = "report.export";
}
