package ru.sfedu.teamselection.dto.integration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Команда трека в плоском виде, без заявок, технологий и вложенных студентов.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationTeamDto {
    private Long id;
    private String name;
    private String projectDescription;
    /**
     * Имя типа проекта из справочника project_types; null, если тип не выбран.
     */
    private String projectType;
    /**
     * Капитан берётся из teams.captain_id — это то же поле, по которому команду
     * читает выгрузка в Excel. null, если капитана нет.
     */
    private Long captainStudentId;
    private Boolean isFull;
    private Integer quantityOfStudents;
}
