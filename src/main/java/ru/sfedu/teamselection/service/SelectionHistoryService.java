package ru.sfedu.teamselection.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.domain.SelectionCounts;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.domain.stats.SelectionDailyStats;
import ru.sfedu.teamselection.dto.SelectionHistoryDto;
import ru.sfedu.teamselection.repository.SelectionDailyStatsRepository;

/**
 * Набор по дням для графика в обзоре (#49).
 *
 * <p>Ряд начинается с первого записанного дня: где записей не было, график молчит, а не рисует нули,
 * которых никто не считал. День без изменений повторяет предыдущий. Сегодня считается вживую —
 * строка за сегодня обновляется только при изменениях и может отставать. После закрытия окна ряд
 * кончается последним днём окна: график — про набор, а не про правки администраторов после него.
 */
@RequiredArgsConstructor
@Service
public class SelectionHistoryService {

    private final TrackService trackService;
    private final SelectionDailyStatsRepository repository;
    private final SelectionDailyStatsService statsService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SelectionHistoryDto history() {
        Track track = trackService.getActive();
        return new SelectionHistoryDto(track.getId(), track.getStartDate(), track.getEndDate(), days(track));
    }

    private List<SelectionHistoryDto.Day> days(Track track) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = track.getStartDate();
        LocalDate end = track.getEndDate();
        if (start != null && today.isBefore(start)) {
            return List.of();
        }

        NavigableMap<LocalDate, SelectionDailyStats> stored = new TreeMap<>();
        repository.findAllByTrackIdOrderByDayAsc(track.getId())
                .forEach(row -> stored.put(row.getDay(), row));

        LocalDate last = end != null && end.isBefore(today) ? end : today;
        LocalDate first = stored.isEmpty() ? today : stored.firstKey();
        // Строки раньше начала окна бывают, если начало окна перенесли позже уже записанных дней.
        if (start != null && first.isBefore(start)) {
            first = start;
        }

        List<SelectionHistoryDto.Day> days = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            if (day.equals(today)) {
                days.add(live(day, statsService.countsFor(track.getId())));
            } else {
                // Первый день ряда — записанный, так что строка не позже него есть всегда.
                days.add(stored(day, stored.floorEntry(day).getValue()));
            }
        }
        return days;
    }

    private static SelectionHistoryDto.Day live(LocalDate day, SelectionCounts counts) {
        return new SelectionHistoryDto.Day(
                day, counts.totalTeams(), counts.completeTeams(), counts.studentsInTeams(), counts.registered());
    }

    private static SelectionHistoryDto.Day stored(LocalDate day, SelectionDailyStats row) {
        return new SelectionHistoryDto.Day(
                day, row.getTotalTeams(), row.getCompleteTeams(), row.getStudentsInTeams(), row.getRegistered());
    }
}
