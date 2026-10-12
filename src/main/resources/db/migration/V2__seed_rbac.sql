-- Seed the permission catalog and system roles. Idempotent.

INSERT INTO permissions (code, module, action, description) VALUES
    ('user.read',              'user',         'read',              'View users'),
    ('user.manage',            'user',         'manage',            'Create, update and deactivate users'),
    ('user.manage_permissions','user',         'manage_permissions','Grant or revoke roles and permissions'),
    ('role.manage',            'role',         'manage',            'Create and edit roles'),
    ('audit.read',             'audit',        'read',              'Read audit events'),
    ('catalog.read',           'catalog',      'read',              'View service catalog'),
    ('catalog.manage',         'catalog',      'manage',            'Manage service catalog and tax config'),
    ('crm.read',               'crm',          'read',              'View leads, contacts and activities'),
    ('crm.manage',             'crm',          'manage',            'Manage leads, contacts and activities'),
    ('invoice.read',           'invoice',      'read',              'View invoices'),
    ('invoice.create',         'invoice',      'create',            'Create draft invoices'),
    ('invoice.issue',          'invoice',      'issue',             'Issue invoices'),
    ('invoice.cancel',         'invoice',      'cancel',            'Cancel invoices and raise credit notes'),
    ('invoice.download',       'invoice',      'download',          'Download invoice PDFs'),
    ('payment.read',           'payment',      'read',              'View payments'),
    ('payment.record',         'payment',      'record',            'Record payments manually'),
    ('payment.verify',         'payment',      'verify',            'Verify or reject submitted payments'),
    ('subscription.read',      'subscription', 'read',              'View subscriptions'),
    ('subscription.manage',    'subscription', 'manage',            'Manage subscriptions and plans'),
    ('notification.manage',    'notification', 'manage',            'Manage email templates and outbox'),
    ('report.read',            'report',       'read',              'View dashboards and reports'),
    ('report.export',          'report',       'export',            'Export reports')
ON CONFLICT (code) DO NOTHING;

-- System roles: rank establishes the super-admin boundary.
INSERT INTO roles (code, name, description, rank, system_role) VALUES
    ('SUPER_ADMIN', 'Super Administrator', 'Unrestricted system owner',            100, TRUE),
    ('ADMIN',       'Administrator',       'Day-to-day administrator',              80, TRUE),
    ('ACCOUNTANT',  'Accountant',          'Billing and payment operations',        60, TRUE),
    ('SALES',       'Sales',               'CRM and lead operations',               40, TRUE),
    ('VIEWER',      'Viewer',              'Read-only access',                      10, TRUE)
ON CONFLICT (code) DO NOTHING;

-- SUPER_ADMIN holds every permission (kept in sync on each migration run).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

-- ADMIN holds everything except permission management.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN' AND p.code NOT IN ('user.manage_permissions')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'catalog.read', 'invoice.read', 'invoice.create', 'invoice.issue', 'invoice.download',
    'payment.read', 'payment.record', 'payment.verify',
    'subscription.read', 'subscription.manage', 'report.read', 'report.export'
) WHERE r.code = 'ACCOUNTANT'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'catalog.read', 'crm.read', 'crm.manage', 'invoice.read', 'subscription.read', 'report.read'
) WHERE r.code = 'SALES'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'catalog.read', 'crm.read', 'invoice.read', 'payment.read', 'subscription.read', 'report.read'
) WHERE r.code = 'VIEWER'
ON CONFLICT DO NOTHING;
