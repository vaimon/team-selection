package ru.sfedu.teamselection.dto.integration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Студент трека в плоском виде. Свободный текст «о себе» и контакты наружу не
 * отдаются: потребителя у них нет, а это персональные данные.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationStudentDto {
    private Long id;
    private String fio;
    private String email;
    private Integer course;
    private Integer groupNumber;
    private Boolean hasTeam;
    /**
     * Признак со стороны студента. Может разойтись с captainStudentId команды —
     * потребитель решает, что делать с расхождением.
     */
    private Boolean isCaptain;
    /**
     * Текущая команда студента; null, если он ещё не в команде.
     */
    private Long teamId;
}
