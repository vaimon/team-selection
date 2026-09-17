package ru.sfedu.teamselection.dto.track;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.sfedu.teamselection.util.validation.DateRange;
import ru.sfedu.teamselection.util.validation.ValidDateRange;

/**
 * Starts the next selection. Dates are required on this DTO, so a selection created over HTTP can
 * never end up unable to open or unable to close (#6). Targets and type are copied from the current
 * selection, the name defaults to «Набор &lt;year&gt;».
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidDateRange
public class NewSelectionDto implements DateRange {
    private String name;
    private String about;
    @NotNull(message = "Дата начала набора обязательна")
    private LocalDate startDate;
    @NotNull(message = "Дата окончания набора обязательна")
    private LocalDate endDate;
}
