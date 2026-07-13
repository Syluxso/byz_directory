-- Soft-delete for directory tenants; allow slug reuse after delete.

ALTER TABLE directory.tenants
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE directory.tenants
    DROP CONSTRAINT IF EXISTS uq_tenants_org_slug;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenants_org_slug_active
    ON directory.tenants (organization_id, slug)
    WHERE deleted_at IS NULL AND slug IS NOT NULL;
