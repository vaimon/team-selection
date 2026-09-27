package ru.sfedu.teamselection.service;

import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import ru.sfedu.teamselection.BasicTestContainerTest;
import ru.sfedu.teamselection.SelectionWindowFixture;
import ru.sfedu.teamselection.TeamSelectionApplication;
import ru.sfedu.teamselection.domain.Team;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.domain.stats.SelectionDailyStats;
import ru.sfedu.teamselection.dto.AdminOverviewDto;
import ru.sfedu.teamselection.dto.ProjectTypeDto;
import ru.sfedu.teamselection.dto.team.TeamCreationDto;
import ru.sfedu.teamselection.repository.SelectionDailyStatsRepository;
import ru.sfedu.teamselection.repository.StudentRepository;
import ru.sfedu.teamselection.repository.TrackRepository;
import ru.sfedu.teamselection.repository.UserRepository;

/**
 * Строка за сегодня догоняет состав после каждого изменения (#49).
 *
 * <p>Без {@code @Transactional}: запись идёт после коммита, а тест, откатывающий всё в конце, коммита
 * не делает никогда. Поэтому каждый шаг здесь коммитится по-настоящему, а в конце тест возвращает
 * сид как был — контейнер базы может переиспользоваться следующими классами.
 *
 * <p>Сид: команда 1 — тимлид студент 2 и участник студент 12; студент 4 свободен.
 */
@SpringBootTest(classes = TeamSelectionApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@TestPropertySource("/application-test.yml")
class SelectionDailyStatsRecordingTest extends BasicTestContainerTest {

    private static final long TEAM = 1L;
    private static final long FREE_STUDENT = 4L;

    @Autowired
    private TeamService teamService;
    @Autowired
    private AdminOverviewService overviewService;
    @Autowired
    private SelectionDailyStatsRepository repository;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;

    private User admin;
    private Track track;
    private LocalDate seededStart;
    private LocalDate seededEnd;
    private long lastActivityId;
    private Long createdTeamId;

    @BeforeEach
    void openTheWindow() {
        admin = userRepository.findById(1L).orElseThrow();
        track = trackRepository.findByActiveTrue().orElseThrow();
        seededStart = track.getStartDate();
        seededEnd = track.getEndDate();
        lastActivityId = jdbcTemplate.queryForObject("select coalesce(max(id), 0) from activity_log", Long.class);
        SelectionWindowFixture.open(trackRepository);
        jdbcTemplate.update("delete from selection_daily_stats");
    }

    @AfterEach
    void putTheSeedBack() {
        if (createdTeamId != null && jdbcTemplate.queryForObject(
                "select count(*) from teams where id = ?", Integer.class, createdTeamId) > 0) {
            teamService.delete(createdTeamId, admin);
        }
        if (studentRepository.findById(FREE_STUDENT).orElseThrow().getCurrentTeam() != null) {
            teamService.removeMember(TEAM, FREE_STUDENT, admin);
        }
        jdbcTemplate.update("delete from selection_daily_stats");
        jdbcTemplate.update("delete from activity_log where id > ?", lastActivityId);
        Track active = trackRepository.findByActiveTrue().orElseThrow();
        active.setStartDate(seededStart);
        active.setEndDate(seededEnd);
        trackRepository.save(active);
    }

    private SelectionDailyStats today() {
        return repository.findById(new SelectionDailyStats.Key(track.getId(), LocalDate.now(clock)))
                .orElseThrow(() -> new AssertionError("за сегодня ничего не записано"));
    }

    /** Строка за сегодня говорит то же, что обзор: он считает те же вещи и проверен отдельно. */
    private void assertTodayMatchesTheOverview() {
        AdminOverviewDto overview = overviewService.overview(3);
        SelectionDailyStats row = today();
        Assertions.assertEquals(overview.teams().total(), row.getTotalTeams(), "команд всего");
        Assertions.assertEquals(overview.teams().complete(), row.getCompleteTeams(), "собрано");
        Assertions.assertEquals(overview.students().withTeam(), row.getStudentsInTeams(), "в командах");
        Assertions.assertEquals(overview.students().total(), row.getRegistered(), "зарегистрировались");
    }

    @Test
    void aJoinIsInTodaysRowOnceItIsCommitted() {
        int before = overviewService.overview(3).students().withTeam();

        teamService.addStudentToTeam(TEAM, FREE_STUDENT, admin);

        assertTodayMatchesTheOverview();
        Assertions.assertEquals(before + 1, today().getStudentsInTeams());
    }

    /**
     * Роспуск пишется в историю до удаления команды — пока её имя ещё можно прочитать. Строка за
     * сегодня всё равно должна увидеть команду уже удалённой: пересчёт идёт после коммита.
     */
    @Test
    void aDisbandIsCountedAfterTheTeamIsGone() {
        int teamsBefore = overviewService.overview(3).teams().total();

        Team created = teamService.create(TeamCreationDto.builder()
                .name("history by day")
                .projectType(new ProjectTypeDto().id(1L))
                .captainId(FREE_STUDENT)
                .build(), admin);
        createdTeamId = created.getId();
        Assertions.assertEquals(teamsBefore + 1, today().getTotalTeams());

        teamService.delete(createdTeamId, admin);

        assertTodayMatchesTheOverview();
        Assertions.assertEquals(teamsBefore, today().getTotalTeams());
    }
}
