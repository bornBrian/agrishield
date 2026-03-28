-- TABLE: inventory (Current stock levels for each organization)
CREATE TABLE inventory (
    inventory_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID REFERENCES organisations(org_id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(product_id),
    current_stock INTEGER DEFAULT 0,
    min_stock_alert INTEGER DEFAULT 10,
    last_restock_date TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- TABLE: consumers (The Farmers/End Users)
CREATE TABLE consumers (
    consumer_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(200),
    phone VARCHAR(20) UNIQUE NOT NULL,
    region VARCHAR(64),
    district VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- TABLE: sales (Final transaction from Dealer to Farmer)
CREATE TABLE sales (
    sale_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dealer_org_id UUID REFERENCES organisations(org_id),
    user_id UUID REFERENCES users(user_id), -- The staff member who sold it
    consumer_id UUID REFERENCES consumers(consumer_id),
    sale_date TIMESTAMPTZ DEFAULT NOW(),
    total_amount NUMERIC(15,2),
    payment_method VARCHAR(20) DEFAULT 'cash' -- cash, mobile_money, credit
);

-- TABLE: sale_items (Linking serials to a specific sale)
CREATE TABLE sale_items (
    sale_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id UUID REFERENCES sales(sale_id) ON DELETE CASCADE,
    serial_id UUID REFERENCES serials(serial_id),
    unit_price NUMERIC(15,2)
);