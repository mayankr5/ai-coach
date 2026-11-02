-- Coach feedback is kept with each check-in so the history shows what the coach said
ALTER TABLE check_ins
    ADD COLUMN coach_message TEXT,
    ADD COLUMN updated_at    TIMESTAMPTZ NOT NULL DEFAULT now();

-- Where a plan change came from: first plan, AI re-plan, rule-based fallback, or nightly job
ALTER TABLE plan_revisions
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'INITIAL';

-- Set once a skipped/partial task has been handed to the re-planner, so it is never carried forward twice
ALTER TABLE tasks
    ADD COLUMN carried_over BOOLEAN NOT NULL DEFAULT FALSE;
