package ru.sfedu.teamselection.dto.track;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import ru.sfedu.teamselection.util.validation.ValidDateRange;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidDateRange
public class TrackCreationDto {
    @NotNull
    @NotBlank
    private String name;
    private String about;
    private LocalDate startDate;
    private LocalDate endDate;
    @NotNull
    private String type;
    @Min(0)
    private Integer firstYearTarget;
    @Min(0)
    private Integer secondYearTarget;
}
