package ru.sfedu.teamselection.enums;

import java.time.LocalDate;

/**
 * Где сегодняшний день относительно окна набора. Границы включительные: в день начала набор уже
 * открыт, в день окончания ещё открыт, закрывается со следующего дня.
 *
 * <p>Незаданная граница считается закрытой: набор без даты начала не открывается, набор без даты
 * окончания сразу закрыт. Так администратор замечает недонастроенный набор в первый же день, а не
 * обнаруживает в ноябре, что приём заявок так и не прекратился.
 */
public enum SelectionWindowState {
    NOT_OPEN,
    OPEN,
    CLOSED;

    public static SelectionWindowState of(LocalDate startDate, LocalDate endDate, LocalDate today) {
        if (startDate == null || today.isBefore(startDate)) {
            return NOT_OPEN;
        }
        if (endDate == null || today.isAfter(endDate)) {
            return CLOSED;
        }
        return OPEN;
    }
}
