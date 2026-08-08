-- Optional page placement + who may dismiss/complete from the product UI.
ALTER TABLE directory.guided_tasks
    ADD COLUMN IF NOT EXISTS display_route VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dismissal VARCHAR(32) NOT NULL DEFAULT 'user';

COMMENT ON COLUMN directory.guided_tasks.display_route IS
    'When null/blank: show on Home (/app/me) only. When set: show on routes matching this prefix.';
COMMENT ON COLUMN directory.guided_tasks.dismissal IS
    'user = subject may dismiss/complete; system = service/system only.';
