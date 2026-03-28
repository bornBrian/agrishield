-- 1. Create a reusable function to update 'updated_at' columns
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 2. Apply Triggers to main tables
CREATE TRIGGER update_organisations_modtime BEFORE UPDATE ON organisations FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();
CREATE TRIGGER update_products_modtime BEFORE UPDATE ON products FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();
CREATE TRIGGER update_batches_modtime BEFORE UPDATE ON batches FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();
CREATE TRIGGER update_inventory_modtime BEFORE UPDATE ON inventory FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

-- 3. Create Performance Indexes (Makes the system fast as it grows)
CREATE INDEX idx_serials_batch_id ON serials(batch_id);
CREATE INDEX idx_serials_status ON serials(status);
CREATE INDEX idx_transfer_items_transfer_id ON transfer_items(transfer_id);
CREATE INDEX idx_inventory_org_product ON inventory(org_id, product_id);
CREATE INDEX idx_audit_logs_record_id ON audit_logs(record_id);
CREATE INDEX idx_notifications_user_id ON notifications(user_id) WHERE is_read = FALSE;

-- 4. Create a View for easy reporting (Bonus item for your 20-object count)
CREATE VIEW view_product_traceability AS
SELECT 
    s.serial_number,
    p.product_name,
    b.batch_number,
    o.org_name as current_owner,
    s.status
FROM serials s
JOIN batches b ON s.batch_id = b.batch_id
JOIN products p ON b.product_id = p.product_id
JOIN organisations o ON p.org_id = o.org_id;