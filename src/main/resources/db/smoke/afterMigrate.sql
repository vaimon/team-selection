-- Starting state of the journey smoke (vaimon/PDSelectorFrontend#28), loaded only under the smoke
-- profile. Flyway runs afterMigrate on every start, so every statement is safe to repeat and the
-- window is re-centred on today. It does not undo a previous run (teams, applications): the smoke
-- stack starts from an empty database for that.

UPDATE tracks SET is_active = false WHERE is_active AND name <> 'Смоук-набор';

INSERT INTO tracks (name, about, start_date, end_date, type, is_active, first_year_target, second_year_target)
SELECT 'Смоук-набор', 'Набор для сквозного смоук-прогона', current_date - 1, current_date + 14, 'bachelor', false, 3, 3
WHERE NOT EXISTS (SELECT 1 FROM tracks WHERE name = 'Смоук-набор');

UPDATE tracks
SET is_active = true, start_date = current_date - 1, end_date = current_date + 14,
    first_year_target = 3, second_year_target = 3
WHERE name = 'Смоук-набор';

INSERT INTO users (role_id, email, fio, is_enabled, created_at, updated_at)
SELECT r.id, v.email, v.fio, true, now(), now()
FROM (VALUES
          ('admin@smoke.test', 'Админов Админ Смоукович', 'ADMIN'),
          ('lead@smoke.test', 'Лидова Лида Смоуковна', 'STUDENT'),
          ('first@smoke.test', 'Первов Пётр Смоукович', 'STUDENT'),
          ('second@smoke.test', 'Вторая Вера Смоуковна', 'STUDENT')
     ) AS v(email, fio, role)
JOIN roles r ON r.name = v.role
ON CONFLICT DO NOTHING;

-- The admin has no questionnaire, as in prod: the backend refuses one for an admin.
INSERT INTO students (course, group_number, about_self, contacts, user_id, current_track_id)
SELECT v.course, v.group_number, v.about_self, v.contacts, u.id, t.id
FROM (VALUES
          ('lead@smoke.test', 2, 3, 'Соберу команду под веб-проект', '@smoke_lead'),
          ('first@smoke.test', 1, 1, 'Первый курс, ищу команду', '@smoke_first'),
          ('second@smoke.test', 2, 4, 'Второй курс, ищу команду', '@smoke_second')
     ) AS v(email, course, group_number, about_self, contacts)
JOIN users u ON lower(u.email) = v.email
JOIN tracks t ON t.name = 'Смоук-набор'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO students_technologies (student_id, technology_id)
SELECT s.id, tech.id
FROM students s
JOIN users u ON u.id = s.user_id AND u.email = 'lead@smoke.test'
CROSS JOIN (SELECT id FROM technologies ORDER BY id LIMIT 2) AS tech
WHERE NOT EXISTS (
    SELECT 1 FROM students_technologies st WHERE st.student_id = s.id AND st.technology_id = tech.id
);
