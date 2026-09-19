-- Вторая команда активного набора для доски состава (#14): тимлид — студент 4 (1 курс),
-- участник — студент 6 (2 курс). В сиде в активном наборе одна команда, перемещать между
-- командами было бы не из чего.
INSERT INTO teams (id, captain_id, name, project_description, project_type_id, current_track_id, created_at, updated_at)
VALUES (2001, 4, 'Board second', 'вторая команда доски', 1, 1, now(), now());

INSERT INTO teams_students (team_id, student_id) VALUES (2001, 4), (2001, 6);

UPDATE students SET has_team = true, current_team_id = 2001, is_captain = true WHERE id = 4;
UPDATE students SET has_team = true, current_team_id = 2001 WHERE id = 6;
UPDATE applications SET status = 'cancelled' WHERE student_id = 6 AND status = 'sent';
