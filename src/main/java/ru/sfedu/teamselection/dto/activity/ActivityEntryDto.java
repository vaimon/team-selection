package ru.sfedu.teamselection.dto.activity;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import ru.sfedu.teamselection.enums.ActivityAction;

/**
 * Запись истории для админки (#16): готовая строка для списка плюс коды и id — для значка и ссылок.
 *
 * @param actorName пусто, если действие пришло не от человека (вызов из кабинета ПД, первый вход)
 */
public record ActivityEntryDto(
        Long id,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime at,
        ActivityAction action,
        String summary,
        Long actorUserId,
        String actorName,
        String actorEmail,
        Long teamId,
        Long relatedTeamId,
        Long studentId
) {
}
