-- В справочнике технологий лежали записи, которые технологиями не являются (#12):
--   Web, Mobile, Desktop, CrossPlatform — это в точности типы проекта из project_types;
--   GameDev, Analytics — направления проекта, а не то, чем он сделан;
--   Frontend, Backend — роли в команде.
-- Из-за них «Desktop» показывался в фильтрах и как тип проекта, и как технология.
-- Удаляем по имени, а не по id из сида: на проде идентификаторы могли разойтись.
-- Связи просто снимаем — переносить их некуда, тип проекта команда выбирает сама.
DELETE FROM teams_technologies
WHERE technology_id IN (
    SELECT id FROM technologies
    WHERE lower(name) IN ('web', 'mobile', 'desktop', 'crossplatform', 'gamedev', 'analytics', 'frontend', 'backend')
);

DELETE FROM students_technologies
WHERE technology_id IN (
    SELECT id FROM technologies
    WHERE lower(name) IN ('web', 'mobile', 'desktop', 'crossplatform', 'gamedev', 'analytics', 'frontend', 'backend')
);

DELETE FROM technologies
WHERE lower(name) IN ('web', 'mobile', 'desktop', 'crossplatform', 'gamedev', 'analytics', 'frontend', 'backend');
