-- TABLE: audit_logs (Records every action for security)
CREATE TABLE audit_logs (
    log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(user_id) ON DELETE SET NULL,
    org_id UUID REFERENCES organisations(org_id),
    action_type VARCHAR(50) NOT NULL, -- CREATE, UPDATE, DELETE, LOGIN, SCAN
    table_name VARCHAR(50),
    record_id UUID,
    old_value JSONB, -- The data BEFORE the change
    new_value JSONB, -- The data AFTER the change
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- TABLE: notifications (Alerts for users/regulators)
CREATE TABLE notifications (
    notification_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(user_id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    severity VARCHAR(20) DEFAULT 'info', -- info, warning, danger
    link_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- TABLE: suspicious_activities (Flagged duplicate or invalid scans)
CREATE TABLE suspicious_activities (
    incident_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    serial_id UUID REFERENCES serials(serial_id),
    reported_by_user_id UUID REFERENCES users(user_id),
    reported_by_org_id UUID REFERENCES organisations(org_id),
    incident_type VARCHAR(50), -- DUPLICATE_SCAN, INVALID_QR, LOCATION_MISMATCH
    gps_lat NUMERIC(10,7),
    gps_lng NUMERIC(10,7),
    description TEXT,
    status VARCHAR(20) DEFAULT 'open', -- open, investigating, resolved
    created_at TIMESTAMPTZ DEFAULT NOW()
);