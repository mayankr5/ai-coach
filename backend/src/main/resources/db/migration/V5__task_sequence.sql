-- A goal's tasks form one series: the re-planner walks them in this order, and tasks on the same day
-- are listed by it. Spaced by 1000 so a catch-up task can sit right after the task it continues (+1).
ALTER TABLE tasks
    ADD COLUMN sequence_no INT NOT NULL DEFAULT 0;

-- Existing tasks: by day, then id, except that catch-up copies of earlier missed tasks (same title, or
-- "Finish: " + title) go first on their day, which is where the re-planner meant them to be
UPDATE tasks t
SET sequence_no = o.rn * 1000
FROM (SELECT c.id,
             ROW_NUMBER() OVER (PARTITION BY c.goal_id ORDER BY c.scheduled_date,
                 CASE WHEN EXISTS (SELECT 1 FROM tasks m
                                   WHERE m.goal_id = c.goal_id AND m.carried_over
                                     AND m.scheduled_date < c.scheduled_date
                                     AND c.title IN (m.title, 'Finish: ' || m.title)) THEN 0 ELSE 1 END,
                 c.id) AS rn
      FROM tasks c) o
WHERE t.id = o.id;
