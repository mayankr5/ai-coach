-- Which model ("provider/model") produced a revision, now that several models can answer
ALTER TABLE plan_revisions
    ADD COLUMN ai_model VARCHAR(100);
