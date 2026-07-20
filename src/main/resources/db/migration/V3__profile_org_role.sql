-- Org-level role on profiles: admin | member

ALTER TABLE directory.profiles
    ADD COLUMN IF NOT EXISTS org_role VARCHAR(20) NOT NULL DEFAULT 'member';

ALTER TABLE directory.profiles
    DROP CONSTRAINT IF EXISTS chk_profiles_org_role;

ALTER TABLE directory.profiles
    ADD CONSTRAINT chk_profiles_org_role CHECK (org_role IN ('admin', 'member'));

CREATE INDEX IF NOT EXISTS idx_profiles_org_role
    ON directory.profiles (organization_id, org_role);
