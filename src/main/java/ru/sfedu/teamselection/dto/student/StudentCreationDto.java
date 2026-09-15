package ru.sfedu.teamselection.dto.student;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
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
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.dto.TechnologyDto;


/**
 * DTO for creation of {@link Student}
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StudentCreationDto {
    @NotNull
    @Min(1)
    private Integer course;

    @JsonProperty(value = "group_number")
    private Integer groupNumber;

    @JsonProperty(value = "about_self")
    @Size(max = 1024)
    private String aboutSelf;

    @NotBlank(message = "Укажите контакт для связи")
    @Size(max = 255)
    private String contacts;

    @JsonProperty(value = "user_id")
    @NotNull
    private Long userId;

    /** Ignored: registration always joins the current selection. */
    @JsonProperty(value = "current_track_id")
    private Long trackId;

    @Builder.Default
    private List<TechnologyDto> technologies = new ArrayList<>();
}
