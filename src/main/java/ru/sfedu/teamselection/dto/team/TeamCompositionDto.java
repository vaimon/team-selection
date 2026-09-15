package ru.sfedu.teamselection.dto.team;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.sfedu.teamselection.domain.TeamComposition;

/**
 * Who a team still needs: members per year against the effective targets (course 1 = first-years,
 * course 2 and above = second-years).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamCompositionDto {
    @JsonProperty("first_years")
    private int firstYears;

    @JsonProperty("second_years")
    private int secondYears;

    @JsonProperty("first_year_target")
    private int firstYearTarget;

    @JsonProperty("second_year_target")
    private int secondYearTarget;

    @JsonProperty("first_year_places_left")
    private int firstYearPlacesLeft;

    @JsonProperty("second_year_places_left")
    private int secondYearPlacesLeft;

    private boolean complete;

    public static TeamCompositionDto of(TeamComposition composition) {
        return TeamCompositionDto.builder()
                .firstYears(composition.firstYears())
                .secondYears(composition.secondYears())
                .firstYearTarget(composition.firstYearTarget())
                .secondYearTarget(composition.secondYearTarget())
                .firstYearPlacesLeft(composition.firstYearPlacesLeft())
                .secondYearPlacesLeft(composition.secondYearPlacesLeft())
                .complete(composition.complete())
                .build();
    }
}
