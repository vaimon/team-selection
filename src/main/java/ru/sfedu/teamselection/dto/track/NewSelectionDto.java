package ru.sfedu.teamselection.dto.track;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Starts the next selection. Everything is optional: targets and type are copied from the current
 * selection, the name defaults to «Отбор &lt;year&gt;».
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewSelectionDto {
    private String name;
    private String about;
    private LocalDate startDate;
    private LocalDate endDate;
}
