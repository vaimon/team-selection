package ru.sfedu.teamselection.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;

/**
 * Набор по дням (#49) — для графика в обзоре. Даты строками («2026-10-01»): глобальный Jackson
 * пишет LocalDate массивом, а новому полю незачем тащить эту форму.
 *
 * @param trackId   идентификатор текущего набора
 * @param startDate начало окна
 * @param endDate   конец окна
 * @param days      по строке на день, от первого записанного до сегодня (после закрытия — до конца окна)
 */
public record SelectionHistoryDto(
        Long trackId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate startDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate endDate,
        List<Day> days
) {

    /**
     * @param date            день
     * @param totalTeams      команд всего
     * @param completeTeams   из них собраны по цели
     * @param studentsInTeams людей в командах
     * @param registered      зарегистрировалось всего
     */
    public record Day(
            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate date,
            int totalTeams,
            int completeTeams,
            int studentsInTeams,
            int registered
    ) {
    }
}
