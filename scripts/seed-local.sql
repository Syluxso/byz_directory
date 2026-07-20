-- Local seed: Nytech PM tenant aligned with IAM seed (projects/iam/scripts/seed-local.sql).
-- Run AFTER directory has started once (Flyway creates schema).
--
--   Get-Content projects/directory/scripts/seed-local.sql | docker exec -i byz-directory-db psql -U db -d directory

INSERT INTO directory.tenants (id, organization_id, name, slug, description, created_by)
VALUES (
  '10723404-1607-4ba2-8bc5-fd076f0b831e',
  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'Nytech PM',
  'nytech-pm',
  'Local Nytech PM tenant',
  NULL
)
ON CONFLICT (id) DO NOTHING;

-- Attach existing org profiles as tenant members (admin if org admin).
INSERT INTO directory.memberships (tenant_id, user_id, organization_id, role)
SELECT
  '10723404-1607-4ba2-8bc5-fd076f0b831e',
  p.user_id,
  p.organization_id,
  CASE WHEN p.org_role = 'admin' THEN 'admin' ELSE 'user' END
FROM directory.profiles p
WHERE p.organization_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
ON CONFLICT (tenant_id, user_id) DO NOTHING;
