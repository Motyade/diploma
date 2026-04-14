CREATE TABLE credentials (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL UNIQUE,
    phone_number  VARCHAR(20)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL,
    store_id      UUID,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_credentials_user_id ON credentials (user_id);
CREATE INDEX idx_credentials_phone_number ON credentials (phone_number);
