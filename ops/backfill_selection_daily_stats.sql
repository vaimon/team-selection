-- Разовое заполнение selection_daily_stats за прошедшие дни активного набора (#49).
--
-- Зачем: счётчики по дням пишутся после каждого изменения состава только с выкатки #49, а набор к
-- тому моменту уже шёл. Скрипт восстанавливает дни с открытия окна до вчерашнего по истории
-- действий: берёт сегодняшний состав и откатывает activity_log назад, день за днём.
--
-- Как запускать — руками, один раз, после выкатки (Flyway его не видит):
--   docker compose exec -T postgres psql -U <user> -d team_selection -v ON_ERROR_STOP=1 \
--     < ops/backfill_selection_daily_stats.sql
-- Повторный запуск безопасен: дни, за которые строка уже есть, не трогаются (ON CONFLICT DO NOTHING).
--
-- Что восстанавливается точно: вступления, выходы, исключения, перемещения по доске, принятые
-- заявки, создание команд, анкеты. Что приблизительно — и скрипт это считает и печатает:
--   * роспуск: состав распущенной команды в истории не записан, до роспуска она считается
--     командой без людей (итого команд — верно, «в командах» и «собрано» — занижены);
--   * смена целей и курса: прежние значения в истории только текстом — берутся нынешние.
-- Самопроверка в конце: если откатить всю историю набора, должен остаться пустой набор. Если нет —
-- печатается, сколько команд, участий и анкет осталось без объяснения в истории.
DO $$
DECLARE
    v_track      bigint;
    v_start      date;
    v_first      date;
    v_today      date := (now() AT TIME ZONE 'Europe/Moscow')::date;
    v_fy_target  int;
    v_sy_target  int;
    v_day        date;
    v_written    int := 0;
    v_disbands   int;
    v_targets    int;
    v_updates    int;
    e            record;
BEGIN
    SELECT id, start_date, first_year_target, second_year_target
      INTO v_track, v_start, v_fy_target, v_sy_target
      FROM tracks WHERE is_active;
    IF v_track IS NULL THEN
        RAISE EXCEPTION 'Нет активного набора — заполнять нечего';
    END IF;
    IF v_start IS NULL THEN
        RAISE EXCEPTION 'У набора % нет даты начала окна', v_track;
    END IF;

    -- Состояние «сейчас»: кто в какой команде, какие команды есть, кто зарегистрирован.
    CREATE TEMP TABLE bf_members ON COMMIT DROP AS
        SELECT ts.team_id, ts.student_id
          FROM teams_students ts JOIN teams t ON t.id = ts.team_id
         WHERE t.current_track_id = v_track;
    CREATE TEMP TABLE bf_teams ON COMMIT DROP AS
        SELECT id AS team_id, first_year_target AS fy_override, second_year_target AS sy_override
          FROM teams WHERE current_track_id = v_track;
    CREATE TEMP TABLE bf_registered ON COMMIT DROP AS
        SELECT id AS student_id FROM students WHERE current_track_id = v_track;
    -- Курс — нынешний: прежний в истории только текстом.
    CREATE TEMP TABLE bf_course ON COMMIT DROP AS
        SELECT id AS student_id, course FROM students;

    -- Откат назад: сначала сегодняшние записи (их день ещё не кончился), потом день за днём — и дальше
    -- открытия окна, до самой ранней записи набора: самопроверке нужна вся история.
    v_day := v_today;
    v_first := least(v_start, coalesce(
        (SELECT min(created_at)::date FROM activity_log WHERE track_id = v_track), v_start));
    WHILE v_day >= v_first LOOP
        FOR e IN
            SELECT * FROM activity_log
             WHERE track_id = v_track AND created_at::date = v_day
             ORDER BY created_at DESC, id DESC
        LOOP
            CASE e.action
                WHEN 'MEMBER_JOINED' THEN
                    DELETE FROM bf_members WHERE team_id = e.team_id AND student_id = e.student_id;
                WHEN 'MEMBER_LEFT', 'MEMBER_REMOVED' THEN
                    INSERT INTO bf_members VALUES (e.team_id, e.student_id);
                WHEN 'MEMBER_MOVED' THEN
                    IF e.related_team_id IS NOT NULL THEN
                        -- из related_team_id в team_id
                        DELETE FROM bf_members WHERE team_id = e.team_id AND student_id = e.student_id;
                        INSERT INTO bf_members VALUES (e.related_team_id, e.student_id);
                    -- Без related_team_id запись одинакова для «из пула в команду» и «из команды в пул».
                    -- Различает состояние: более поздние записи уже откачены, так что bf_members — это
                    -- состав сразу после этого перемещения. Человек в команде — значит, пришёл в неё.
                    ELSIF EXISTS (SELECT 1 FROM bf_members WHERE team_id = e.team_id AND student_id = e.student_id) THEN
                        -- из пула в team_id
                        DELETE FROM bf_members WHERE team_id = e.team_id AND student_id = e.student_id;
                    ELSE
                        -- из team_id в пул
                        INSERT INTO bf_members VALUES (e.team_id, e.student_id);
                    END IF;
                WHEN 'APPLICATION_ANSWERED' THEN
                    IF e.summary LIKE '%: принята' THEN
                        DELETE FROM bf_members WHERE team_id = e.team_id AND student_id = e.student_id;
                    END IF;
                WHEN 'TEAM_CREATED' THEN
                    DELETE FROM bf_members WHERE team_id = e.team_id;
                    DELETE FROM bf_teams WHERE team_id = e.team_id;
                WHEN 'TEAM_DISBANDED' THEN
                    INSERT INTO bf_teams VALUES (e.team_id, NULL, NULL);
                WHEN 'QUESTIONNAIRE_FILLED' THEN
                    DELETE FROM bf_registered WHERE student_id = e.student_id;
                WHEN 'STUDENT_DELETED' THEN
                    INSERT INTO bf_registered VALUES (e.student_id);
                ELSE
                    NULL; -- на счётчики не влияет или восстановимо только приблизительно (см. шапку)
            END CASE;
        END LOOP;

        -- Теперь состояние — на конец предыдущего дня.
        v_day := v_day - 1;
        IF v_day >= v_start AND v_day < v_today THEN
            INSERT INTO selection_daily_stats
                (track_id, day, total_teams, complete_teams, students_in_teams, registered, updated_at)
            SELECT v_track, v_day,
                   (SELECT count(*) FROM bf_teams),
                   (SELECT count(*) FROM (
                        SELECT t.team_id
                          FROM bf_teams t
                          LEFT JOIN bf_members m ON m.team_id = t.team_id
                          LEFT JOIN bf_course c ON c.student_id = m.student_id
                         GROUP BY t.team_id, t.fy_override, t.sy_override
                        HAVING count(*) FILTER (WHERE c.course = 1) >= coalesce(t.fy_override, v_fy_target)
                           AND count(m.student_id) FILTER (WHERE c.course IS DISTINCT FROM 1)
                               >= coalesce(t.sy_override, v_sy_target)
                    ) complete),
                   (SELECT count(DISTINCT student_id) FROM bf_members),
                   (SELECT count(*) FROM bf_registered),
                   now() AT TIME ZONE 'Europe/Moscow'
            ON CONFLICT (track_id, day) DO NOTHING;
            IF FOUND THEN
                v_written := v_written + 1;
                RAISE NOTICE 'День %: записан', v_day;
            ELSE
                RAISE NOTICE 'День %: строка уже есть, не тронут', v_day;
            END IF;
        END IF;
    END LOOP;

    SELECT count(*) FILTER (WHERE action = 'TEAM_DISBANDED'),
           count(*) FILTER (WHERE action IN ('TARGETS_CHANGED', 'SELECTION_SETTINGS_CHANGED')),
           count(*) FILTER (WHERE action = 'STUDENT_UPDATED')
      INTO v_disbands, v_targets, v_updates
      FROM activity_log WHERE track_id = v_track;

    RAISE NOTICE 'Набор %: записано дней — %', v_track, v_written;
    RAISE NOTICE 'Приблизительно: роспусков — %, смен целей и настроек — %, правок анкет — %',
        v_disbands, v_targets, v_updates;
    RAISE NOTICE 'Самопроверка (до первой записи набора должно быть 0/0/0): команд — %, участий — %, анкет — %',
        (SELECT count(*) FROM bf_teams), (SELECT count(*) FROM bf_members), (SELECT count(*) FROM bf_registered);
END $$;
