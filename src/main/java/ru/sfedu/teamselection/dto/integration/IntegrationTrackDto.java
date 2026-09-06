package ru.sfedu.teamselection.dto.integration;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Трек в виде, отдаваемом интеграционным API: только то, что внешняя система
 * сопоставляет со своим понятием сезона.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationTrackDto {
    private Long id;
    private String name;
    private String type;
    // Глобально в проекте включена сериализация дат массивом [y,m,d]; менять её
    // нельзя — на этом стоит существующий фронт. Для интеграционного контракта
    // нужен ISO-8601, поэтому формат задан точечно на полях.
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate startDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate endDate;
}
