package ru.sfedu.teamselection.dto.board;

import jakarta.validation.constraints.NotNull;

public record BoardVersionRequest(@NotNull Long version) {
}
