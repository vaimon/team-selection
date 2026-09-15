-- One active track = the current selection; per-year targets replace total min/max and the 2nd-year cap.
ALTER TABLE tracks
    ADD COLUMN is_active boolean NOT NULL DEFAULT false,
    ADD COLUMN first_year_target integer NOT NULL DEFAULT 3 CHECK (first_year_target >= 0),
    ADD COLUMN second_year_target integer NOT NULL DEFAULT 3 CHECK (second_year_target >= 0),
    DROP COLUMN min_constraint,
    DROP COLUMN max_constraint,
    DROP COLUMN max_second_course_constraint;

CREATE UNIQUE INDEX tracks_single_active ON tracks ((true)) WHERE is_active;

UPDATE tracks
SET is_active = true
WHERE id = (SELECT id FROM tracks ORDER BY start_date DESC NULLS LAST, id LIMIT 1);

-- Per-team overrides (null = track target). Counters were stored and went stale; they are derived now.
ALTER TABLE teams
    ADD COLUMN first_year_target integer CHECK (first_year_target >= 0),
    ADD COLUMN second_year_target integer CHECK (second_year_target >= 0),
    DROP COLUMN is_full,
    DROP COLUMN quantity_of_students;
