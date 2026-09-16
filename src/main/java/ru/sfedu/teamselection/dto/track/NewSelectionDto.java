package ru.sfedu.teamselection.dto.track;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.sfedu.teamselection.util.validation.DateRange;
import ru.sfedu.teamselection.util.validation.ValidDateRange;

/**
 * Starts the next selection. Everything is optional: targets and type are copied from the current
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
    private LocalDate startDate;
    private LocalDate endDate;
}
