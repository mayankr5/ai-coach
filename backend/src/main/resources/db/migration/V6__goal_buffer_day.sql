-- One weekday per goal kept free of planned tasks, so catch-up work has somewhere to go and shifts stop there.
-- Existing goals get none: their plans already use every day.
ALTER TABLE goals
    ADD COLUMN buffer_day VARCHAR(9);
