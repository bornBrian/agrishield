-- Create Enum Types
CREATE TYPE org_status AS ENUM ('pending', 'approved', 'suspended');
CREATE TYPE org_type AS ENUM ('manufacturer', 'distributor', 'dealer', 'regulator');
CREATE TYPE user_status AS ENUM ('active', 'suspended', 'locked');
CREATE TYPE user_role AS ENUM ('manufacturer', 'regulator', 'distributor', 'dealer', 'admin');

-- TABLE: organisations
CREATE TABLE organisations (
    org_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_name VARCHAR(200) NOT NULL,
    org_type org_type NOT NULL,
    license_number VARCHAR(64),
    tin_number VARCHAR(20),
    region VARCHAR(64),
    address TEXT,
    gps_lat NUMERIC(10,7),
    gps_lng NUMERIC(10,7),
    contact_email VARCHAR(200) UNIQUE,
    contact_phone VARCHAR(20),
    status org_status DEFAULT 'pending',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- TABLE: users
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID REFERENCES organisations(org_id),
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(200) UNIQUE NOT NULL,
    phone VARCHAR(20),
    password_hash TEXT NOT NULL,
    role user_role NOT NULL,
    totp_required BOOLEAN DEFAULT FALSE,
    status user_status DEFAULT 'active',
    failed_logins INTEGER DEFAULT 0,
    locked_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);