-- Extra minutes the user can handle on catch-up days, on top of minutes_per_day
ALTER TABLE goals
    ADD COLUMN extra_minutes_per_day INT NOT NULL DEFAULT 0;
