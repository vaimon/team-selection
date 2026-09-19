package ru.sfedu.teamselection.dto.board;

import jakarta.validation.constraints.NotNull;

/**
 * Перемещение студента. Без fromTeamId студент берётся из пула, без toTeamId — уходит в пул.
 *
 * @param fromVersion     версия исходной команды с доски; обязательна, если команда указана
 * @param toVersion       версия целевой команды с доски; обязательна, если команда указана
 * @param allowOverTarget организатор сознательно превышает цель по курсу студента
 * @param newLeadId       преемник, если перемещается тимлид
 */
public record BoardMoveRequest(
        @NotNull Long studentId,
        Long fromTeamId,
        Long fromVersion,
        Long toTeamId,
        Long toVersion,
        boolean allowOverTarget,
        Long newLeadId
) {
}
