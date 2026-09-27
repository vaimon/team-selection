-- Счётчики набора по дням (#49) — для графика «сколько команд собралось к какому дню» в обзоре.
-- Строка за сегодня переписывается после каждого изменения состава, поэтому к концу дня в ней то,
-- с чем день закончился. Внешнего ключа на tracks нет, как и у activity_log: запись о том, что было,
-- переживает то, о чём рассказывает. Строк — десятки в год, очистки не нужно.
CREATE TABLE selection_daily_stats (
    track_id          bigint    NOT NULL,
    day               date      NOT NULL,
    total_teams       integer   NOT NULL,
    complete_teams    integer   NOT NULL,
    students_in_teams integer   NOT NULL,
    registered        integer   NOT NULL,
    updated_at        timestamp without time zone NOT NULL,
    PRIMARY KEY (track_id, day)
);
