-- Directory schema: profiles, org profiles, tenants, memberships, invites, reusable cards.
-- organization_id always refers to an IAM organization UUID (orgs are not created here).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE directory.contact_cards (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL,
    email           VARCHAR(320),
    phone           VARCHAR(50),
    website         VARCHAR(500),
    label           VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_contact_cards_org ON directory.contact_cards(organization_id);

CREATE TABLE directory.address_cards (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL,
    line1           VARCHAR(255),
    line2           VARCHAR(255),
    city            VARCHAR(120),
    region          VARCHAR(120),
    postal_code     VARCHAR(40),
    country         VARCHAR(2),
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    label           VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_address_cards_org ON directory.address_cards(organization_id);

CREATE TABLE directory.organization_profiles (
    organization_id UUID         PRIMARY KEY,
    display_name    VARCHAR(255),
    contact_card_id UUID         REFERENCES directory.contact_cards(id) ON DELETE SET NULL,
    address_card_id UUID         REFERENCES directory.address_cards(id) ON DELETE SET NULL,
    metadata        JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE directory.profiles (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL,
    organization_id UUID         NOT NULL,
    email           VARCHAR(320) NOT NULL,
    display_name    VARCHAR(255),
    contact_card_id UUID         REFERENCES directory.contact_cards(id) ON DELETE SET NULL,
    address_card_id UUID         REFERENCES directory.address_cards(id) ON DELETE SET NULL,
    metadata        JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_profiles_user_org UNIQUE (user_id, organization_id),
    CONSTRAINT uq_profiles_org_email UNIQUE (organization_id, email)
);

CREATE INDEX idx_profiles_org ON directory.profiles(organization_id);
CREATE INDEX idx_profiles_email ON directory.profiles(organization_id, lower(email));

CREATE TABLE directory.tenants (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100),
    description     TEXT,
    created_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenants_org_slug UNIQUE (organization_id, slug)
);

CREATE INDEX idx_tenants_org ON directory.tenants(organization_id);

CREATE TABLE directory.memberships (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES directory.tenants(id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL,
    organization_id UUID         NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'user',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_memberships_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT chk_memberships_role CHECK (role IN ('admin', 'user'))
);

CREATE INDEX idx_memberships_user ON directory.memberships(user_id);
CREATE INDEX idx_memberships_org ON directory.memberships(organization_id);
CREATE INDEX idx_memberships_tenant ON directory.memberships(tenant_id);

CREATE TABLE directory.invites (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL,
    tenant_id       UUID         NOT NULL REFERENCES directory.tenants(id) ON DELETE CASCADE,
    email           VARCHAR(320) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'user',
    token           VARCHAR(64)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'pending',
    invited_by      UUID,
    expires_at      TIMESTAMPTZ,
    responded_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_invites_token UNIQUE (token),
    CONSTRAINT chk_invites_role CHECK (role IN ('admin', 'user')),
    CONSTRAINT chk_invites_status CHECK (status IN ('pending', 'accepted', 'rejected', 'expired', 'revoked'))
);

CREATE INDEX idx_invites_pending_email
    ON directory.invites(organization_id, lower(email))
    WHERE status = 'pending';
CREATE INDEX idx_invites_tenant ON directory.invites(tenant_id);
CREATE INDEX idx_invites_token ON directory.invites(token);
