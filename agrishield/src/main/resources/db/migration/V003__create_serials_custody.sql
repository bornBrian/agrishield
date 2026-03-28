-- Create Enum for Item Status
CREATE TYPE item_status AS ENUM ('manufactured', 'in_transit', 'stocked', 'sold', 'recalled', 'expired');

-- TABLE: serials (The individual units/bottles/bags)
CREATE TABLE serials (
    serial_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id UUID REFERENCES batches(batch_id) ON DELETE CASCADE,
    serial_number VARCHAR(128) UNIQUE NOT NULL,
    qr_code_hash TEXT UNIQUE NOT NULL,
    status item_status DEFAULT 'manufactured',
    last_scanned_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- TABLE: custody_transfers (The "Chain of Custody")
CREATE TABLE custody_transfers (
    transfer_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_org_id UUID REFERENCES organisations(org_id),
    to_org_id UUID REFERENCES organisations(org_id),
    sender_user_id UUID REFERENCES users(user_id),
    receiver_user_id UUID REFERENCES users(user_id),
    transfer_date TIMESTAMPTZ DEFAULT NOW(),
    status VARCHAR(20) DEFAULT 'pending', -- pending, completed, rejected
    notes TEXT
);

-- TABLE: transfer_items (Linking serials to a specific transfer)
CREATE TABLE transfer_items (
    transfer_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID REFERENCES custody_transfers(transfer_id) ON DELETE CASCADE,
    serial_id UUID REFERENCES serials(serial_id),
    scanned_at TIMESTAMPTZ DEFAULT NOW()
);