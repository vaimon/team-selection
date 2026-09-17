package ru.sfedu.teamselection.service;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.BasicTestContainerTest;
import ru.sfedu.teamselection.TeamSelectionApplication;
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.TeamComposition;
import ru.sfedu.teamselection.dto.AdminOverviewDto;
import ru.sfedu.teamselection.repository.StudentRepository;
import ru.sfedu.teamselection.repository.TeamRepository;
import ru.sfedu.teamselection.repository.TrackRepository;

/**
 * Обзор набора для организатора (#10). Счётчики сверяются с тем же сидом, по которому считались, —
 * но независимо: тест берёт данные из репозиториев и складывает их сам.
 */
@SpringBootTest(classes = TeamSelectionApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
@ActiveProfiles("test")
@TestPropertySource("/application-test.yml")
class AdminOverviewServiceTest extends BasicTestContainerTest {

    @Autowired
    private AdminOverviewService underTest;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private TeamRepository teamRepository;

    private Long activeTrackId() {
        return trackRepository.findByActiveTrue().orElseThrow().getId();
    }

    @Test
    void theOverviewNamesTheCurrentSelection() {
        AdminOverviewDto overview = underTest.overview(3);

        Assertions.assertEquals(activeTrackId(), overview.trackId());
        Assertions.assertEquals(
                trackRepository.findByActiveTrue().orElseThrow().getName(), overview.trackName());
    }

    @Test
    void theStudentCountersAddUpAndSplitByYear() {
        List<Student> seeded = studentRepository.findAllByCurrentTrackId(activeTrackId());

        AdminOverviewDto.Students students = underTest.overview(3).students();

        Assertions.assertEquals(seeded.size(), students.total());
        Assertions.assertEquals(students.total(), students.firstYear() + students.secondYear());
        Assertions.assertEquals(students.total(), students.withTeam() + students.withoutTeam());
        Assertions.assertEquals(
                students.withoutTeam(),
                students.firstYearWithoutTeam() + students.secondYearWithoutTeam());
    }

    @Test
    void theYearSplitMatchesTheCourseOfEveryStudent() {
        long expectedFirstYears = studentRepository.findAllByCurrentTrackId(activeTrackId()).stream()
                .filter(student -> TeamComposition.isFirstYear(student.getCourse()))
                .count();

        Assertions.assertEquals(expectedFirstYears, underTest.overview(3).students().firstYear());
    }

    @Test
    void theTeamCountersSplitCompleteFromIncomplete() {
        long seededTeams = teamRepository.findAllByCurrentTrackId(activeTrackId()).size();

        AdminOverviewDto.Teams teams = underTest.overview(3).teams();

        Assertions.assertEquals(seededTeams, teams.total());
        Assertions.assertEquals(teams.total(), teams.complete() + teams.incomplete());
    }

    @Test
    void pendingApplicationsAreCountedSeparatelyByType() {
        AdminOverviewDto.Applications applications = underTest.overview(3).applications();

        Assertions.assertTrue(applications.pendingRequests() >= 0);
        Assertions.assertTrue(applications.pendingInvites() >= 0);
        Assertions.assertTrue(applications.staleRequests() <= applications.pendingRequests());
        Assertions.assertTrue(applications.staleInvites() <= applications.pendingInvites());
    }

    /**
     * Сид создаёт заявки «сейчас», поэтому с порогом в один день залежавшихся быть не может, а с
     * нулевым — все незакрытые уже считаются залежавшимися. Так проверяется, что порог действительно
     * участвует в подсчёте, а не игнорируется.
     */
    @Test
    void theThresholdActuallyFilters() {
        AdminOverviewDto.Applications freshlyCreated = underTest.overview(1).applications();
        AdminOverviewDto.Applications everything = underTest.overview(0).applications();

        Assertions.assertEquals(0, freshlyCreated.staleRequests());
        Assertions.assertEquals(0, freshlyCreated.staleInvites());
        Assertions.assertEquals(everything.pendingRequests(), everything.staleRequests());
        Assertions.assertEquals(everything.pendingInvites(), everything.staleInvites());
    }

    @Test
    void theThresholdIsEchoedBackSoTheReaderKnowsWhatWasCounted() {
        Assertions.assertEquals(7, underTest.overview(7).applications().staleThresholdDays());
    }

    /**
     * Ровно то состояние, в котором набор окажется сразу после сброса прода: трек создан, в нём ещё
     * никого. Проверяем, что обзор отвечает нулями, а не падает на пустых выборках.
     */
    @Test
    @Sql(statements = {
            "UPDATE tracks SET is_active = false WHERE is_active",
            "INSERT INTO tracks (id, name, about, start_date, end_date, type, is_active,"
                    + " first_year_target, second_year_target)"
                    + " VALUES (777, 'Пустой набор', '', current_date, current_date + 30, 'bachelor', true, 3, 3)"
    })
    void anEmptySelectionReportsZeroesRatherThanFailing() {
        AdminOverviewDto overview = underTest.overview(3);

        Assertions.assertEquals(0, overview.students().total());
        Assertions.assertEquals(0, overview.teams().total());
        Assertions.assertEquals(0, overview.applications().pendingRequests());
        Assertions.assertEquals(0, overview.applications().pendingInvites());
    }
}
