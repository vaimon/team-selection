package ru.sfedu.teamselection.dto.board;

import jakarta.validation.constraints.NotNull;

public record BoardLeadRequest(@NotNull Long version, @NotNull Long studentId) {
}
