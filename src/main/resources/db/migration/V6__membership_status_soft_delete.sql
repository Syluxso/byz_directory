-- Membership lifecycle: status (active/blocked/removed), soft-delete, no invite expiry default.

ALTER TABLE directory.memberships
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'active',
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

UPDATE directory.memberships
SET status = 'active'
WHERE status IS NULL OR status = '';

ALTER TABLE directory.memberships
    DROP CONSTRAINT IF EXISTS chk_memberships_status;

ALTER TABLE directory.memberships
    ADD CONSTRAINT chk_memberships_status
        CHECK (status IN ('active', 'blocked', 'removed'));

CREATE INDEX IF NOT EXISTS idx_memberships_tenant_status
    ON directory.memberships(tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_memberships_user_status
    ON directory.memberships(user_id, status);

-- Invites no longer expire by product rule; leave column nullable and clear existing expiries.
UPDATE directory.invites
SET expires_at = NULL
WHERE status = 'pending' AND expires_at IS NOT NULL;
