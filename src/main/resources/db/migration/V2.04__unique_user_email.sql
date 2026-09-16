-- Вход ищет пользователя по почте, но UNIQUE на users.email никогда не было: вторая строка с той же
-- почтой ломает вход этому человеку (NonUniqueResultException в findByEmailFetchRole).
-- Регистр не различаем — Azure отдаёт адрес так, как он записан в профиле, а человек тот же.
CREATE UNIQUE INDEX users_email_lower_unique ON users (lower(email));
