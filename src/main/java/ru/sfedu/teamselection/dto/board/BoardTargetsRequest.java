package ru.sfedu.teamselection.dto.board;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Переопределение целей команды; null по курсу возвращает цель набора. */
public record BoardTargetsRequest(
        @NotNull Long version,
        @Min(0) Integer firstYearTarget,
        @Min(0) Integer secondYearTarget
) {
}
