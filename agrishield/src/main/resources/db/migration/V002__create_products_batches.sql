-- Create Enum for Batch Status
CREATE TYPE batch_status AS ENUM ('production', 'testing', 'certified', 'distributed', 'recalled');

-- TABLE: categories
CREATE TABLE categories (
    category_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- TABLE: products
CREATE TABLE products (
    product_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID REFERENCES organisations(org_id),
    category_id UUID REFERENCES categories(category_id),
    product_name VARCHAR(200) NOT NULL,
    common_name VARCHAR(200),
    registration_number VARCHAR(64) UNIQUE,
    description TEXT,
    composition TEXT,
    usage_instructions TEXT,
    safety_precautions TEXT,
    image_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- TABLE: batches
CREATE TABLE batches (
    batch_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID REFERENCES products(product_id),
    batch_number VARCHAR(64) UNIQUE NOT NULL,
    manufacturing_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    quantity_produced NUMERIC(15,2),
    unit_of_measure VARCHAR(20),
    status batch_status DEFAULT 'production',
    qr_code_base_data TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);