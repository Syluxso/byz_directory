-- Device × public-IP intel ledger (MaxMind Insights). No FK to iam.devices.

CREATE TABLE directory.device_ip_intel (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       UUID         NOT NULL,
    user_id               UUID         NOT NULL,
    device_id             UUID         NOT NULL,
    ip                    VARCHAR(64)  NOT NULL,
    country               TEXT,
    country_iso           CHAR(2),
    continent             TEXT,
    continent_code        CHAR(2),
    region                TEXT,
    city                  TEXT,
    postal_code           VARCHAR(16),
    latitude              DOUBLE PRECISION,
    longitude             DOUBLE PRECISION,
    accuracy_radius_km    INTEGER,
    time_zone             VARCHAR(64),
    asn                   INTEGER,
    as_org                TEXT,
    isp                   TEXT,
    organization          TEXT,
    connection_type       VARCHAR(64),
    user_type             VARCHAR(64),
    static_ip_score       DOUBLE PRECISION,
    user_count            INTEGER,
    is_anonymous          BOOLEAN,
    is_anonymous_vpn      BOOLEAN,
    is_hosting            BOOLEAN,
    is_public_proxy       BOOLEAN,
    is_tor                BOOLEAN,
    is_residential_proxy  BOOLEAN,
    mobile_country_code   VARCHAR(8),
    mobile_network_code   VARCHAR(8),
    raw_json              JSONB,
    source                VARCHAR(32)  NOT NULL DEFAULT 'maxmind_insights',
    status                VARCHAR(16)  NOT NULL,
    first_seen_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    looked_up_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_device_ip_intel_device_ip UNIQUE (device_id, ip),
    CONSTRAINT chk_device_ip_intel_status CHECK (status IN ('success', 'error'))
);

CREATE INDEX idx_device_ip_intel_ip ON directory.device_ip_intel (ip);
CREATE INDEX idx_device_ip_intel_device_seen ON directory.device_ip_intel (device_id, last_seen_at DESC);
CREATE INDEX idx_device_ip_intel_org_user ON directory.device_ip_intel (organization_id, user_id);
CREATE INDEX idx_device_ip_intel_ip_success ON directory.device_ip_intel (ip) WHERE status = 'success';
