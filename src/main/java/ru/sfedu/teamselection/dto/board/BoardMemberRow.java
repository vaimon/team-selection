package ru.sfedu.teamselection.dto.board;

/**
 * Строка выборки доски: команда и один её участник. У команды без участников поля студента — null.
 * Проекция, а не сущности: у Student жадные связи, и загрузка сущностей давала бы запрос на каждого.
 */
public record BoardMemberRow(
        Long teamId,
        String teamName,
        Long version,
        Long captainId,
        Integer firstYearOverride,
        Integer secondYearOverride,
        Long studentId,
        String studentName,
        Integer course,
        Integer groupNumber,
        Boolean isCaptain
) {
}
