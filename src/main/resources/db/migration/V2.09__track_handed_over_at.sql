-- Отметка передачи состава в кабинет ПД (#15). После неё набор только для чтения для всех,
-- включая администраторов: правка здесь молча разошлась бы с core. NULL — набор ещё не передан.
ALTER TABLE tracks
    ADD COLUMN handed_over_at timestamp without time zone;
