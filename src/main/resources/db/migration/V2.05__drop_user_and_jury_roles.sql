-- Ролей остаётся две: STUDENT и ADMIN. JURY не использовалась вовсе, а USER означала «вошёл, но
-- ещё не участник набора» — это состояние теперь выражено отсутствием строки в students
-- (см. ROLE_PARTICIPANT в CurrentAuthoritiesResolver), а не отдельной ролью.
UPDATE users
SET role_id = (SELECT id FROM roles WHERE name = 'STUDENT')
WHERE role_id IN (SELECT id FROM roles WHERE name IN ('USER', 'JURY'));

DELETE FROM roles WHERE name IN ('USER', 'JURY');
