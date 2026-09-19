package ru.sfedu.teamselection.dto.integration;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/**
 * Ответ на передачу состава: какой трек и когда стал только для чтения. При повторном вызове
 * время то же, что и в первый раз.
 */
public record IntegrationHandOverDto(
        Long trackId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime handedOverAt
) {
}
