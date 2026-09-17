package ru.sfedu.teamselection;

import java.time.LocalDate;
import ru.sfedu.teamselection.repository.TrackRepository;

/**
 * Двигает окно активного набора относительно сегодняшнего дня.
 *
 * <p>Сид-набор из миграций закрылся в прошлом, поэтому тесты, которые проверяют не окно, а правила
 * команд и заявок, обязаны открыть его явно. Границы окна разобраны в SelectionWindowServiceTest —
 * здесь только «заведомо открыто / заведомо рано / заведомо поздно».
 */
public final class SelectionWindowFixture {

    private SelectionWindowFixture() {
    }

    public static void open(TrackRepository trackRepository) {
        moveWindow(trackRepository, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
    }

    public static void notOpenYet(TrackRepository trackRepository) {
        moveWindow(trackRepository, LocalDate.now().plusDays(1), LocalDate.now().plusDays(10));
    }

    public static void closed(TrackRepository trackRepository) {
        moveWindow(trackRepository, LocalDate.now().minusDays(10), LocalDate.now().minusDays(1));
    }

    private static void moveWindow(TrackRepository trackRepository, LocalDate startDate, LocalDate endDate) {
        var active = trackRepository.findByActiveTrue().orElseThrow();
        active.setStartDate(startDate);
        active.setEndDate(endDate);
        trackRepository.save(active);
    }
}
