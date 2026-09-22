package ru.sfedu.teamselection;

import java.time.LocalDate;
import java.time.ZoneId;
import ru.sfedu.teamselection.repository.TrackRepository;

/**
 * Двигает окно активного набора относительно сегодняшнего дня.
 *
 * <p>Сид-набор из миграций закрылся в прошлом, поэтому тесты, которые проверяют не окно, а правила
 * команд и заявок, обязаны открыть его явно. Границы окна разобраны в SelectionWindowServiceTest —
 * здесь только «заведомо открыто / заведомо рано / заведомо поздно».
 */
public final class SelectionWindowFixture {

    // «Сегодня» сервиса — по часам TimeConfig (Москва). JVM на CI живёт в UTC, и с 21:00 до 24:00 UTC
    // её «завтра» у сервиса уже «сегодня»: окно, открывающееся завтра, оказывалось открытым.
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Europe/Moscow");

    private SelectionWindowFixture() {
    }

    public static void open(TrackRepository trackRepository) {
        moveWindow(trackRepository, today().minusDays(1), today().plusDays(1));
    }

    public static void notOpenYet(TrackRepository trackRepository) {
        moveWindow(trackRepository, today().plusDays(1), today().plusDays(10));
    }

    public static void closed(TrackRepository trackRepository) {
        moveWindow(trackRepository, today().minusDays(10), today().minusDays(1));
    }

    private static LocalDate today() {
        return LocalDate.now(SERVICE_ZONE);
    }

    private static void moveWindow(TrackRepository trackRepository, LocalDate startDate, LocalDate endDate) {
        var active = trackRepository.findByActiveTrue().orElseThrow();
        active.setStartDate(startDate);
        active.setEndDate(endDate);
        trackRepository.save(active);
    }
}
