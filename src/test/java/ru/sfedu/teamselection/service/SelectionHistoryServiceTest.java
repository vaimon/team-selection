package ru.sfedu.teamselection.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.BasicTestContainerTest;
import ru.sfedu.teamselection.TeamSelectionApplication;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.domain.stats.SelectionDailyStats;
import ru.sfedu.teamselection.dto.AdminOverviewDto;
import ru.sfedu.teamselection.dto.SelectionHistoryDto;
import ru.sfedu.teamselection.exception.NotFoundException;
import ru.sfedu.teamselection.repository.SelectionDailyStatsRepository;
import ru.sfedu.teamselection.repository.TrackRepository;

/**
 * Набор по дням (#49): какие дни попадают в ряд и откуда берётся каждое число.
 *
 * <p>Часы свои — 6 октября, полдень по Москве; окно набора — с 1 по 31 октября. Сегодняшние числа
 * сверяются с обзором: он считает те же вещи и проверен отдельно.
 */
@SpringBootTest(classes = TeamSelectionApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
@ActiveProfiles("test")
@TestPropertySource("/application-test.yml")
class SelectionHistoryServiceTest extends BasicTestContainerTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    @Autowired
    private TrackService trackService;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private SelectionDailyStatsRepository repository;
    @Autowired
    private SelectionDailyStatsService statsService;
    @Autowired
    private AdminOverviewService overviewService;

    private Track track;

    @BeforeEach
    void windowInOctober() {
        repository.deleteAll();
        track = trackRepository.findByActiveTrue().orElseThrow();
        window(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    }

    private void window(LocalDate start, LocalDate end) {
        track.setStartDate(start);
        track.setEndDate(end);
        trackRepository.save(track);
    }

    private SelectionHistoryService on(LocalDate day) {
        Clock clock = Clock.fixed(day.atTime(12, 0).atZone(MOSCOW).toInstant(), MOSCOW);
        return new SelectionHistoryService(trackService, repository, statsService, clock);
    }

    private void stored(LocalDate day, int total, int complete, int inTeams, int registered) {
        repository.save(SelectionDailyStats.builder()
                .trackId(track.getId())
                .day(day)
                .totalTeams(total)
                .completeTeams(complete)
                .studentsInTeams(inTeams)
                .registered(registered)
                .updatedAt(day.atTime(23, 0))
                .build());
    }

    private static SelectionHistoryDto.Day day(LocalDate date, int total, int complete, int inTeams, int registered) {
        return new SelectionHistoryDto.Day(date, total, complete, inTeams, registered);
    }

    /** Сегодня так, как его видит обзор. */
    private SelectionHistoryDto.Day live(LocalDate date) {
        AdminOverviewDto overview = overviewService.overview(3);
        return day(date, overview.teams().total(), overview.teams().complete(),
                overview.students().withTeam(), overview.students().total());
    }

    @Test
    void theSeriesStartsAtTheFirstStoredDayAndCarriesQuietDaysForward() {
        stored(LocalDate.of(2026, 10, 2), 2, 0, 3, 10);
        stored(LocalDate.of(2026, 10, 4), 3, 1, 5, 12);

        SelectionHistoryDto history = on(TODAY).history();

        Assertions.assertEquals(track.getId(), history.trackId());
        Assertions.assertEquals(LocalDate.of(2026, 10, 1), history.startDate());
        Assertions.assertEquals(LocalDate.of(2026, 10, 31), history.endDate());
        Assertions.assertEquals(List.of(
                day(LocalDate.of(2026, 10, 2), 2, 0, 3, 10),
                day(LocalDate.of(2026, 10, 3), 2, 0, 3, 10),
                day(LocalDate.of(2026, 10, 4), 3, 1, 5, 12),
                day(LocalDate.of(2026, 10, 5), 3, 1, 5, 12),
                live(TODAY)
        ), history.days());
    }

    /** Строка за сегодня отстаёт от состава до следующего изменения — график её не показывает. */
    @Test
    void todayIsCountedLiveRatherThanReadFromItsRow() {
        stored(LocalDate.of(2026, 10, 5), 1, 0, 1, 1);
        stored(TODAY, 999, 999, 999, 999);

        List<SelectionHistoryDto.Day> days = on(TODAY).history().days();

        Assertions.assertEquals(live(TODAY), days.get(days.size() - 1));
    }

    @Test
    void withNothingStoredTheSeriesIsTodayAlone() {
        Assertions.assertEquals(List.of(live(TODAY)), on(TODAY).history().days());
    }

    /**
     * После закрытия администраторы ещё правят состав, но график — про окно набора: он кончается
     * последним днём окна тем, с чем этот день закончился.
     */
    @Test
    void afterTheWindowClosesTheSeriesEndsOnItsLastDay() {
        window(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4));
        stored(LocalDate.of(2026, 10, 2), 2, 0, 3, 10);
        stored(LocalDate.of(2026, 10, 3), 3, 1, 5, 12);

        List<SelectionHistoryDto.Day> days = on(TODAY).history().days();

        Assertions.assertEquals(List.of(
                day(LocalDate.of(2026, 10, 2), 2, 0, 3, 10),
                day(LocalDate.of(2026, 10, 3), 3, 1, 5, 12),
                day(LocalDate.of(2026, 10, 4), 3, 1, 5, 12)
        ), days);
    }

    @Test
    void beforeTheWindowOpensThereAreNoDays() {
        window(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 31));

        Assertions.assertEquals(List.of(), on(TODAY).history().days());
    }

    @Test
    void withoutASelectionThereIsNoHistory() {
        track.setActive(false);
        trackRepository.saveAndFlush(track);

        Assertions.assertThrows(NotFoundException.class, () -> on(TODAY).history());
    }
}
