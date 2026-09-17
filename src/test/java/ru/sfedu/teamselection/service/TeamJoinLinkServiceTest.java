package ru.sfedu.teamselection.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.BasicTestContainerTest;
import ru.sfedu.teamselection.SelectionWindowFixture;
import ru.sfedu.teamselection.TeamSelectionApplication;
import ru.sfedu.teamselection.domain.Team;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.dto.team.TeamJoinPreviewDto;
import ru.sfedu.teamselection.enums.ApplicationStatus;
import ru.sfedu.teamselection.enums.JoinRefusalReason;
import ru.sfedu.teamselection.exception.ConstraintViolationException;
import ru.sfedu.teamselection.exception.ForbiddenException;
import ru.sfedu.teamselection.exception.NotFoundException;
import ru.sfedu.teamselection.repository.ApplicationRepository;
import ru.sfedu.teamselection.repository.StudentRepository;
import ru.sfedu.teamselection.repository.TeamRepository;
import ru.sfedu.teamselection.repository.TrackRepository;
import ru.sfedu.teamselection.repository.UserRepository;

/**
 * Ссылка-приглашение (#13).
 *
 * <p>Команда 1 из сида: тимлид — студент 2 (пользователь 3), участник — студент 12 (пользователь 13).
 * Студент 4 (пользователь 5) свободен и состоит в том же наборе 1. У администратора (пользователь 1)
 * анкеты нет вовсе — на нём и проверяется урезанное превью.
 */
@SpringBootTest(classes = TeamSelectionApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
@ActiveProfiles("test")
@TestPropertySource("/application-test.yml")
class TeamJoinLinkServiceTest extends BasicTestContainerTest {
    private static final Long TEAM = 1L;
    private static final Long CAPTAIN_USER = 3L;
    private static final Long MEMBER_USER = 13L;
    private static final Long FREE_USER = 5L;
    private static final Long ADMIN_USER = 1L;
    /** Студент 1: свободен, курс 1, и у него есть неотвеченная заявка в другую команду. */
    private static final Long APPLICANT_USER = 2L;

    @Autowired
    private TeamJoinLinkService underTest;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private ApplicationRepository applicationRepository;

    @BeforeEach
    void openTheSelectionWindow() {
        SelectionWindowFixture.open(trackRepository);
    }

    private User user(Long id) {
        return userRepository.findById(id).orElseThrow();
    }

    private String issuedToken() {
        return underTest.issue(TEAM, user(CAPTAIN_USER));
    }

    // --- выпуск и отключение ---

    @Test
    void theCaptainIssuesALinkAndItResolvesBackToTheTeam() {
        String token = issuedToken();

        Assertions.assertNotNull(token);
        Assertions.assertEquals(TEAM, teamRepository.findByJoinToken(token).orElseThrow().getId());
    }

    @Test
    void reissuingReplacesTheOldTokenAndTheOldLinkStopsWorking() {
        String first = issuedToken();
        String second = issuedToken();

        Assertions.assertNotEquals(first, second);
        Assertions.assertTrue(teamRepository.findByJoinToken(first).isEmpty());
        Assertions.assertEquals(TEAM, teamRepository.findByJoinToken(second).orElseThrow().getId());
    }

    @Test
    void aPlainMemberCannotIssueALink() {
        Assertions.assertThrows(ForbiddenException.class, () -> underTest.issue(TEAM, user(MEMBER_USER)));
    }

    @Test
    void anAdminCannotIssueALinkForATeamTheyDoNotLead() {
        Assertions.assertThrows(ForbiddenException.class, () -> underTest.issue(TEAM, user(ADMIN_USER)));
    }

    @Test
    void theCaptainDisablesTheLinkAndItStopsResolving() {
        String token = issuedToken();

        underTest.disable(TEAM, user(CAPTAIN_USER));

        Assertions.assertTrue(teamRepository.findByJoinToken(token).isEmpty());
        Assertions.assertNull(teamRepository.findById(TEAM).orElseThrow().getJoinToken());
    }

    @Test
    void anAdminDisablesALeakedLink() {
        String token = issuedToken();

        underTest.disable(TEAM, user(ADMIN_USER));

        Assertions.assertTrue(teamRepository.findByJoinToken(token).isEmpty());
    }

    @Test
    void anOutsiderCannotDisableTheLink() {
        issuedToken();

        Assertions.assertThrows(ForbiddenException.class, () -> underTest.disable(TEAM, user(FREE_USER)));
    }

    @Test
    void theCaptainAndTheAdminBothReadTheCurrentToken() {
        String token = issuedToken();

        Assertions.assertEquals(token, underTest.currentToken(TEAM, user(CAPTAIN_USER)));
        Assertions.assertEquals(token, underTest.currentToken(TEAM, user(ADMIN_USER)));
    }

    // --- превью ---

    @Test
    void thePreviewShowsTheTeamAndItsPerYearCounters() {
        TeamJoinPreviewDto preview = underTest.preview(issuedToken(), user(FREE_USER));

        Team team = teamRepository.findById(TEAM).orElseThrow();
        Assertions.assertEquals(team.getName(), preview.getTeamName());
        Assertions.assertEquals(TEAM, preview.getTeamId());
        Assertions.assertEquals(team.getStudents().size(), preview.getFirstYears() + preview.getSecondYears());
    }

    @Test
    void aParticipantSeesTheNameOfTheTeamLead() {
        TeamJoinPreviewDto preview = underTest.preview(issuedToken(), user(FREE_USER));

        Assertions.assertNotNull(preview.getCaptainName());
    }

    @Test
    void aCallerWithoutAQuestionnaireSeesTheTeamButNotTheLeadsName() {
        TeamJoinPreviewDto preview = underTest.preview(issuedToken(), user(ADMIN_USER));

        Assertions.assertNull(preview.getCaptainName());
        Assertions.assertNotNull(preview.getTeamName());
        Assertions.assertFalse(preview.isCanJoin());
        Assertions.assertEquals(JoinRefusalReason.NOT_A_PARTICIPANT, preview.getRefusalReason());
    }

    @Test
    void aFreeStudentOfTheSameSelectionCanJoinAccordingToThePreview() {
        TeamJoinPreviewDto preview = underTest.preview(issuedToken(), user(FREE_USER));

        Assertions.assertTrue(preview.isCanJoin());
        Assertions.assertNull(preview.getRefusalReason());
    }

    @Test
    void thePreviewTellsAMemberOfThisTeamWhyTheyCannotJoin() {
        TeamJoinPreviewDto preview = underTest.preview(issuedToken(), user(MEMBER_USER));

        Assertions.assertFalse(preview.isCanJoin());
        Assertions.assertEquals(JoinRefusalReason.ALREADY_IN_THIS_TEAM, preview.getRefusalReason());
    }

    @Test
    void thePreviewReportsAClosedWindow() {
        String token = issuedToken();
        SelectionWindowFixture.closed(trackRepository);

        TeamJoinPreviewDto preview = underTest.preview(token, user(FREE_USER));

        Assertions.assertFalse(preview.isCanJoin());
        Assertions.assertEquals(JoinRefusalReason.WINDOW_CLOSED, preview.getRefusalReason());
    }

    @Test
    void anUnknownTokenIsNotFound() {
        Assertions.assertThrows(NotFoundException.class,
                () -> underTest.preview("no-such-token", user(FREE_USER)));
    }

    // --- вход по ссылке ---

    @Test
    void aFreeStudentJoinsInOneStep() {
        String token = issuedToken();

        Team actual = underTest.join(token, user(FREE_USER));

        Assertions.assertTrue(actual.getStudents().stream()
                .anyMatch(student -> student.getUser().getId().equals(FREE_USER)));
        Assertions.assertTrue(studentRepository.findByUserId(FREE_USER).getHasTeam());
    }

    /**
     * Пользователь 2 — студент 1, у него в сиде висит заявка 4 в команду 3. Вход по ссылке должен
     * закрыть её так же, как это делает принятая заявка (#8), иначе человек остаётся в очереди в
     * команду, в которую уже не попадёт.
     */
    @Test
    void joiningCancelsTheCallersOtherPendingApplications() {
        String token = issuedToken();
        Assertions.assertEquals(ApplicationStatus.SENT,
                applicationRepository.findById(4L).orElseThrow().status());

        underTest.join(token, user(APPLICANT_USER));

        Assertions.assertEquals(ApplicationStatus.CANCELLED,
                applicationRepository.findById(4L).orElseThrow().status());
    }

    @Test
    void someoneAlreadyInATeamCannotJoin() {
        Assertions.assertThrows(ConstraintViolationException.class,
                () -> underTest.join(issuedToken(), user(MEMBER_USER)));
    }

    @Test
    void joiningIsRefusedAfterTheSelectionCloses() {
        String token = issuedToken();
        SelectionWindowFixture.closed(trackRepository);

        Assertions.assertThrows(ForbiddenException.class, () -> underTest.join(token, user(FREE_USER)));
    }

    @Test
    void aRegeneratedLinkRejectsTheOldToken() {
        String old = issuedToken();
        issuedToken();

        Assertions.assertThrows(NotFoundException.class, () -> underTest.join(old, user(FREE_USER)));
    }

    @Test
    void aDisabledLinkRejectsTheToken() {
        String token = issuedToken();
        underTest.disable(TEAM, user(CAPTAIN_USER));

        Assertions.assertThrows(NotFoundException.class, () -> underTest.join(token, user(FREE_USER)));
    }

    @Test
    void issuingIsRefusedAfterTheSelectionCloses() {
        SelectionWindowFixture.closed(trackRepository);

        Assertions.assertThrows(ForbiddenException.class, () -> underTest.issue(TEAM, user(CAPTAIN_USER)));
    }

    @Test
    void disablingStillWorksAfterTheSelectionCloses() {
        String token = issuedToken();
        SelectionWindowFixture.closed(trackRepository);

        underTest.disable(TEAM, user(CAPTAIN_USER));

        Assertions.assertTrue(teamRepository.findByJoinToken(token).isEmpty());
    }

    // --- остальные причины отказа ---

    /** Обнуляем места для первого курса: студент 4 как раз первокурсник. */
    private void fillUpTheFirstYearPlaces() {
        Team team = teamRepository.findById(TEAM).orElseThrow();
        team.setFirstYearTarget(0);
        teamRepository.save(team);
    }

    @Test
    void thePreviewReportsThatTheCallersYearIsFull() {
        String token = issuedToken();
        fillUpTheFirstYearPlaces();

        TeamJoinPreviewDto preview = underTest.preview(token, user(FREE_USER));

        Assertions.assertFalse(preview.isCanJoin());
        Assertions.assertEquals(JoinRefusalReason.NO_PLACES_FOR_THE_YEAR, preview.getRefusalReason());
    }

    @Test
    void joiningIsRefusedWhenTheCallersYearIsFull() {
        String token = issuedToken();
        fillUpTheFirstYearPlaces();

        Assertions.assertThrows(ConstraintViolationException.class,
                () -> underTest.join(token, user(FREE_USER)));
    }

    /** Пользователь 4 — студент 3, он участвует в наборе 2, а команда живёт в наборе 1. */
    @Test
    void thePreviewReportsThatTheCallerIsInAnotherSelection() {
        TeamJoinPreviewDto preview = underTest.preview(issuedToken(), user(4L));

        Assertions.assertFalse(preview.isCanJoin());
        Assertions.assertEquals(JoinRefusalReason.ANOTHER_SELECTION, preview.getRefusalReason());
    }

    @Test
    @Sql(statements = {
            "INSERT INTO teams (id, captain_id, name, project_type_id, current_track_id, created_at, updated_at)"
                    + " VALUES (900, 4, 'Другая команда', 1, 1, now(), now())",
            "INSERT INTO teams_students (team_id, student_id) VALUES (900, 4)",
            "UPDATE students SET has_team = true, current_team_id = 900 WHERE id = 4"
    })
    void thePreviewReportsThatTheCallerAlreadyHasAnotherTeam() {
        TeamJoinPreviewDto preview = underTest.preview(issuedToken(), user(FREE_USER));

        Assertions.assertFalse(preview.isCanJoin());
        Assertions.assertEquals(JoinRefusalReason.ALREADY_IN_A_TEAM, preview.getRefusalReason());
    }

    @Test
    void aPlainMemberCannotDisableTheLink() {
        issuedToken();

        Assertions.assertThrows(ForbiddenException.class, () -> underTest.disable(TEAM, user(MEMBER_USER)));
    }
}
