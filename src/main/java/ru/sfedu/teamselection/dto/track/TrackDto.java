package ru.sfedu.teamselection.dto.track;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Min;
import ru.sfedu.teamselection.dto.team.TeamDto;
import ru.sfedu.teamselection.enums.SelectionWindowState;
import ru.sfedu.teamselection.util.validation.DateRange;
import ru.sfedu.teamselection.util.validation.ValidDateRange;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidDateRange
public class TrackDto implements DateRange {
    private Long id;
    private String name;
    private String about;
    private LocalDate startDate;
    private LocalDate endDate;
    private String type;
    @Min(0)
    private Integer firstYearTarget;
    @Min(0)
    private Integer secondYearTarget;
    private Boolean active;

    /** Где сегодняшний день относительно окна набора; фронт по нему решает, что показывать. */
    private SelectionWindowState windowState;

    /**
     * Когда состав передан в кабинет ПД; null — набор ещё здесь. Задаётся только ручками передачи.
     * Строкой ISO, а не глобальным массивом: поле новое, старый фронт на его формат не завязан.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime handedOverAt;

    @Builder.Default
    private List<TeamDto> currentTeams = new ArrayList<>();
}

