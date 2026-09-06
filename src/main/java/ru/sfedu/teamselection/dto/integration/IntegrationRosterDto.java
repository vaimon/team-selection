package ru.sfedu.teamselection.dto.integration;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Полный состав трека одним ответом: сам трек, его команды и его студенты.
 * Связь студент → команда живёт в {@link IntegrationStudentDto#getTeamId()},
 * поэтому вложенности и циклов в payload нет.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationRosterDto {
    private IntegrationTrackDto track;
    @Builder.Default
    private List<IntegrationTeamDto> teams = new ArrayList<>();
    @Builder.Default
    private List<IntegrationStudentDto> students = new ArrayList<>();
}
