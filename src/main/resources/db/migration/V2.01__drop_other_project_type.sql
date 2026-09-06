-- Тип проекта «Other» убирается из справочника: он ничего не говорит о проекте и
-- не имеет соответствия в смежной системе, которая читает состав трека.
-- Команды, у которых он был выбран, остаются без типа — колонка nullable и у
-- большинства старых команд и так пуста; тип такие команды выбирают заново.
UPDATE teams
SET project_type_id = NULL
WHERE project_type_id IN (SELECT id FROM project_types WHERE name = 'Other');

DELETE FROM project_types WHERE name = 'Other';
