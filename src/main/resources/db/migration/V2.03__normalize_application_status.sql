-- Статусы заявок писались в верхнем регистре (ApplicationStatus.name()), а сравнивались и
-- искались в нижнем. Канонический регистр — нижний: так лежат сиды и так пишет toString().
UPDATE applications SET status = lower(status);
