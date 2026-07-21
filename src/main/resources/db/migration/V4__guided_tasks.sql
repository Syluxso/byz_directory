-- Guided / system tasks: platform ledger for business apps (e.g. Hamlet).
-- status is a free-form string; conventional values: open, completed, dismissed, cancelled.
CREATE TABLE directory.guided_tasks (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id    UUID         NOT NULL,
    tenant_id          UUID,
    subject_user_id    UUID         NOT NULL,
    type               VARCHAR(128) NOT NULL,
    status             VARCHAR(64)  NOT NULL DEFAULT 'open',
    priority           VARCHAR(32)  NOT NULL DEFAULT 'normal',
    title              VARCHAR(255),
    body               TEXT,
    action_url         TEXT,
    payload            JSONB,
    source             VARCHAR(64),
    dedupe_key         VARCHAR(128),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at       TIMESTAMPTZ,
    dismissed_at       TIMESTAMPTZ
);

CREATE INDEX idx_guided_tasks_subject_status
    ON directory.guided_tasks (organization_id, subject_user_id, status);

CREATE INDEX idx_guided_tasks_org_type
    ON directory.guided_tasks (organization_id, type);

-- One open task per subject+type+dedupe within an org.
CREATE UNIQUE INDEX uq_guided_tasks_open_dedupe
    ON directory.guided_tasks (
        organization_id,
        subject_user_id,
        type,
        COALESCE(dedupe_key, '')
    )
    WHERE status = 'open';
