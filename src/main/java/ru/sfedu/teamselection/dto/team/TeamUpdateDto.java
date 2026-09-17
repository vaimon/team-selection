package ru.sfedu.teamselection.dto.team;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.sfedu.teamselection.dto.ProjectTypeDto;
import ru.sfedu.teamselection.dto.TechnologyDto;

/**
 * Описательные поля команды. Состав и капитанство сюда не входят намеренно (#9): их меняют
 * выделенные операции, поэтому на каждое изменение есть ровно один способ. Идентификатор команды
 * берётся из пути.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TeamUpdateDto {
    @NotBlank
    private String name;

    @JsonProperty("project_description")
    @Size(max = 1024)
    private String projectDescription;

    @JsonProperty("project_type")
    @NotNull
    private ProjectTypeDto projectType;

    @JsonProperty("technologies")
    @NotNull
    @Builder.Default
    private List<TechnologyDto> technologies = new ArrayList<>();
}
