package ru.sfedu.teamselection.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.BasicTestContainerTest;
import ru.sfedu.teamselection.SelectionWindowFixture;
import ru.sfedu.teamselection.TeamSelectionApplication;
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.dto.board.BoardLeadRequest;
import ru.sfedu.teamselection.dto.board.BoardMoveRequest;
import ru.sfedu.teamselection.dto.board.BoardTargetsRequest;
import ru.sfedu.teamselection.dto.board.BoardVersionRequest;
import ru.sfedu.teamselection.dto.board.CompositionBoardDto;
import ru.sfedu.teamselection.dto.board.CompositionBoardDto.StudentCard;
import ru.sfedu.teamselection.dto.board.CompositionBoardDto.TeamCard;
import ru.sfedu.teamselection.dto.board.CompositionBoardDto.TeamStatus;
import ru.sfedu.teamselection.enums.ApplicationStatus;
import ru.sfedu.teamselection.enums.BoardConflict;
import ru.sfedu.teamselection.exception.ConflictException;
import ru.sfedu.teamselection.repository.StudentRepository;
import ru.sfedu.teamselection.repository.TeamRepository;
import ru.sfedu.teamselection.repository.TrackRepository;
import ru.sfedu.teamselection.repository.UserRepository;

/**
 * Доска состава (#14) против сида. В активном наборе сида одна команда (1: тимлид — студент 2,
 * 1 курс; участник — студент 12, 2 курс), остальные студенты набора — в пуле. Где нужна вторая
 * команда, её добавляет board_second_team.sql (2001: тимлид — студент 4, 1 курс; участник — 6, 2 курс).
 *
 * <p>Окно сид-набора закрыто — ровно та ситуация, в которой организатор и работает с доской.
 */
@SpringBootTest(classes = TeamSelectionApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
@ActiveProfiles("test")
@TestPropertySource(
        value = "/application-test.yml",
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class CompositionBoardServiceTest extends BasicTestContainerTest {

    private static final long TEAM = 1L;
    private static final long SECOND_TEAM = 2001L;
    private static final long LEAD = 2L;
    private static final long SECOND_YEAR_MEMBER = 12L;
    private static final long FREE_FIRST_YEAR = 5L;

    @Autowired
    private CompositionBoardService underTest;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User admin;

    @BeforeEach
    void closeTheWindow() {
        SelectionWindowFixture.closed(trackRepository);
        admin = userRepository.findById(1L).orElseThrow();
    }

    private static TeamCard team(CompositionBoardDto board, long teamId) {
        return board.teams().stream()
                .filter(team -> team.id() == teamId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("на доске нет команды " + teamId));
    }

    private static Set<Long> ids(List<StudentCard> students) {
        return students.stream().map(StudentCard::id).collect(Collectors.toSet());
    }

    private static Set<Long> leads(TeamCard team) {
        return team.members().stream().filter(StudentCard::lead).map(StudentCard::id).collect(Collectors.toSet());
    }

    private static BoardMoveRequest fromPool(long studentId, TeamCard to, boolean allowOverTarget) {
        return new BoardMoveRequest(studentId, null, null, to.id(), to.version(), allowOverTarget, null);
    }

    private static BoardMoveRequest toPool(long studentId, TeamCard from, Long newLeadId) {
        return new BoardMoveRequest(studentId, from.id(), from.version(), null, null, false, newLeadId);
    }

    private void assertRefused(BoardConflict expected, Runnable action) {
        ConflictException refusal = Assertions.assertThrows(ConflictException.class, action::run);
        Assertions.assertEquals(expected, refusal.getCode());
    }

    // --- чтение ---

    @Test
    void theBoardShowsEveryTeamOfTheSelectionWithItsMembers() {
        long trackId = trackRepository.findByActiveTrue().orElseThrow().getId();
        Set<Long> seededTeams = teamRepository.findAllByCurrentTrackId(trackId).stream()
                .map(team -> team.getId())
                .collect(Collectors.toSet());

        CompositionBoardDto board = underTest.board();

        Assertions.assertEquals(trackId, board.trackId());
        Assertions.assertEquals(seededTeams, board.teams().stream().map(TeamCard::id).collect(Collectors.toSet()));
        TeamCard team = team(board, TEAM);
        Assertions.assertEquals(Set.of(LEAD, SECOND_YEAR_MEMBER), ids(team.members()));
        Assertions.assertEquals(LEAD, team.leadId());
        Assertions.assertEquals(Set.of(LEAD), leads(team));
    }

    @Test
    void theBoardCountsYearsAgainstTheTrackTargets() {
        TeamCard team = team(underTest.board(), TEAM);

        Assertions.assertEquals(1, team.firstYears());
        Assertions.assertEquals(1, team.secondYears());
        Assertions.assertEquals(3, team.firstYearTarget());
        Assertions.assertEquals(3, team.secondYearTarget());
        Assertions.assertNull(team.firstYearOverride());
        Assertions.assertNull(team.secondYearOverride());
        Assertions.assertEquals(TeamStatus.INCOMPLETE, team.status());
    }

    @Test
    void thePoolIsEveryRegisteredStudentOfTheSelectionWithoutATeam() {
        long trackId = trackRepository.findByActiveTrue().orElseThrow().getId();
        Set<Long> free = studentRepository.findAllByCurrentTrackId(trackId).stream()
                .filter(student -> !student.getHasTeam())
                .map(Student::getId)
                .collect(Collectors.toSet());

        List<StudentCard> pool = underTest.board().pool();

        Assertions.assertFalse(free.isEmpty());
        Assertions.assertEquals(free, ids(pool));
        Assertions.assertTrue(pool.stream().noneMatch(StudentCard::lead));
    }

    /**
     * Критерий «без N+1»: число SQL-запросов доски не зависит от числа команд и участников. Сначала
     * доска с одной командой, затем с пятью дополнительными, в каждой по участнику — счётчик тот же.
     */
    @Test
    void theNumberOfQueriesDoesNotGrowWithTheNumberOfTeams() {
        long before = statementsToBuildTheBoard();

        for (long i = 0; i < 5; i++) {
            long teamId = 3000 + i;
            long studentId = List.of(1L, 7L, 9L, 11L, 13L).get((int) i);
            jdbcTemplate.update("INSERT INTO teams (id, captain_id, name, current_track_id, created_at, updated_at)"
                    + " VALUES (?, ?, ?, 1, now(), now())", teamId, studentId, "Extra " + i);
            jdbcTemplate.update("INSERT INTO teams_students (team_id, student_id) VALUES (?, ?)", teamId, studentId);
            jdbcTemplate.update("UPDATE students SET has_team = true, is_captain = true, current_team_id = ?"
                    + " WHERE id = ?", teamId, studentId);
        }
        long after = statementsToBuildTheBoard();

        Assertions.assertEquals(6, underTest.board().teams().size());
        Assertions.assertEquals(before, after);
    }

    private long statementsToBuildTheBoard() {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        underTest.board();
        return statistics.getPrepareStatementCount();
    }

    // --- перемещения ---

    @Test
    void aStudentFromThePoolJoinsATeamAndLeavesThePool() {
        TeamCard team = team(underTest.board(), TEAM);

        CompositionBoardDto board = underTest.move(fromPool(FREE_FIRST_YEAR, team, false), admin);

        TeamCard updated = team(board, TEAM);
        Assertions.assertTrue(ids(updated.members()).contains(FREE_FIRST_YEAR));
        Assertions.assertFalse(ids(board.pool()).contains(FREE_FIRST_YEAR));
        Assertions.assertEquals(2, updated.firstYears());
        Assertions.assertEquals(Set.of(LEAD), leads(updated));
    }

    /** Попав в команду, студент больше не может ждать ответа по своим заявкам — они закрываются. */
    @Test
    void joiningFromThePoolClosesTheStudentsPendingApplications() {
        TeamCard team = team(underTest.board(), TEAM);
        Assertions.assertTrue(studentRepository.findById(FREE_FIRST_YEAR).orElseThrow().getApplications().stream()
                .anyMatch(application -> application.status() == ApplicationStatus.SENT));

        underTest.move(fromPool(FREE_FIRST_YEAR, team, false), admin);

        Assertions.assertTrue(studentRepository.findById(FREE_FIRST_YEAR).orElseThrow().getApplications().stream()
                .noneMatch(application -> application.status() == ApplicationStatus.SENT));
    }

    @Test
    void aMemberSentToThePoolIsFreeAgain() {
        TeamCard team = team(underTest.board(), TEAM);

        CompositionBoardDto board = underTest.move(toPool(SECOND_YEAR_MEMBER, team, null), admin);

        Assertions.assertTrue(ids(board.pool()).contains(SECOND_YEAR_MEMBER));
        Assertions.assertEquals(0, team(board, TEAM).secondYears());
    }

    @Test
    @Sql("/sql-scripts/board_second_team.sql")
    void aMemberMovesStraightFromOneTeamToAnother() {
        CompositionBoardDto before = underTest.board();
        TeamCard from = team(before, SECOND_TEAM);
        TeamCard to = team(before, TEAM);

        CompositionBoardDto board = underTest.move(
                new BoardMoveRequest(6L, from.id(), from.version(), to.id(), to.version(), false, null), admin);

        Assertions.assertEquals(Set.of(4L), ids(team(board, SECOND_TEAM).members()));
        Assertions.assertTrue(ids(team(board, TEAM).members()).contains(6L));
        Assertions.assertEquals(2, team(board, TEAM).secondYears());
        Assertions.assertFalse(ids(board.pool()).contains(6L));
    }

    /** Версии в ответе свежие: следующее действие с ними проходит, без повторной загрузки доски. */
    @Test
    void theReturnedBoardCarriesVersionsGoodForTheNextMove() {
        TeamCard team = team(underTest.board(), TEAM);

        CompositionBoardDto afterFirst = underTest.move(fromPool(FREE_FIRST_YEAR, team, false), admin);
        TeamCard updated = team(afterFirst, TEAM);
        Assertions.assertNotEquals(team.version(), updated.version());

        CompositionBoardDto afterSecond = underTest.move(fromPool(9L, updated, false), admin);
        Assertions.assertEquals(3, team(afterSecond, TEAM).firstYears());
    }

    /**
     * Команда 2 — из завершённого набора. Отказ по ней должен прийти до передачи капитанства: если
     * проверка сработает позже, доска в той же транзакции увидит команду 1 уже без тимлида.
     */
    @Test
    void aMoveIntoAnArchivedSelectionIsRefusedBeforeAnythingChanges() {
        TeamCard team = team(underTest.board(), TEAM);
        long archivedVersion = teamRepository.findById(2L).orElseThrow().getVersion();
        BoardMoveRequest intoTheArchive = new BoardMoveRequest(
                LEAD, TEAM, team.version(), 2L, archivedVersion, true, SECOND_YEAR_MEMBER);

        Assertions.assertThrows(RuntimeException.class, () -> underTest.move(intoTheArchive, admin));

        TeamCard unchanged = team(underTest.board(), TEAM);
        Assertions.assertEquals(LEAD, unchanged.leadId());
        Assertions.assertEquals(Set.of(LEAD, SECOND_YEAR_MEMBER), ids(unchanged.members()));
    }

    @Test
    @Sql(statements = "UPDATE students SET current_track_id = 2 WHERE id = 5")
    void aStudentOfAnotherSelectionCannotBeMovedIn() {
        TeamCard team = team(underTest.board(), TEAM);

        Assertions.assertThrows(RuntimeException.class,
                () -> underTest.move(fromPool(FREE_FIRST_YEAR, team, true), admin));

        Assertions.assertFalse(ids(team(underTest.board(), TEAM).members()).contains(FREE_FIRST_YEAR));
    }

    // --- цели ---

    @Test
    void movingBeyondTheTargetIsRefusedUnlessAskedExplicitly() {
        TeamCard team = team(underTest.setTargets(TEAM, new BoardTargetsRequest(
                team(underTest.board(), TEAM).version(), 1, null)), TEAM);

        assertRefused(BoardConflict.OVER_TARGET, () -> underTest.move(fromPool(FREE_FIRST_YEAR, team, false), admin));

        TeamCard over = team(underTest.move(fromPool(FREE_FIRST_YEAR, team, true), admin), TEAM);
        Assertions.assertEquals(2, over.firstYears());
        Assertions.assertEquals(TeamStatus.OVER_TARGET, over.status());
    }

    @Test
    void anOverrideReplacesTheTrackTargetAndClearingItRestoresIt() {
        TeamCard team = team(underTest.board(), TEAM);

        TeamCard overridden = team(underTest.setTargets(TEAM, new BoardTargetsRequest(team.version(), 1, 1)), TEAM);
        Assertions.assertEquals(1, overridden.firstYearOverride());
        Assertions.assertEquals(1, overridden.firstYearTarget());
        Assertions.assertEquals(TeamStatus.COMPLETE, overridden.status());

        TeamCard cleared = team(underTest.setTargets(
                TEAM, new BoardTargetsRequest(overridden.version(), null, null)), TEAM);
        Assertions.assertNull(cleared.firstYearOverride());
        Assertions.assertEquals(3, cleared.firstYearTarget());
        Assertions.assertEquals(TeamStatus.INCOMPLETE, cleared.status());
    }

    // --- тимлид ---

    @Test
    void movingTheLeadWithoutASuccessorIsRefused() {
        TeamCard team = team(underTest.board(), TEAM);

        assertRefused(BoardConflict.LEAD_NEEDS_SUCCESSOR, () -> underTest.move(toPool(LEAD, team, null), admin));

        Assertions.assertEquals(Set.of(LEAD, SECOND_YEAR_MEMBER), ids(team(underTest.board(), TEAM).members()));
    }

    @Test
    void movingTheLeadWithASuccessorHandsTheTeamOver() {
        TeamCard team = team(underTest.board(), TEAM);

        CompositionBoardDto board = underTest.move(toPool(LEAD, team, SECOND_YEAR_MEMBER), admin);

        TeamCard updated = team(board, TEAM);
        Assertions.assertEquals(SECOND_YEAR_MEMBER, updated.leadId());
        Assertions.assertEquals(Set.of(SECOND_YEAR_MEMBER), leads(updated));
        StudentCard formerLead = board.pool().stream().filter(s -> s.id() == LEAD).findFirst().orElseThrow();
        Assertions.assertFalse(formerLead.lead());
    }

    @Test
    void theSuccessorMustBeARemainingMember() {
        TeamCard team = team(underTest.board(), TEAM);

        Assertions.assertThrows(RuntimeException.class,
                () -> underTest.move(toPool(LEAD, team, FREE_FIRST_YEAR), admin));
        Assertions.assertThrows(RuntimeException.class,
                () -> underTest.move(toPool(LEAD, team, LEAD), admin));
    }

    /** Команда без тимлида не возникает никогда: одинокого тимлида не двигают, команду распускают. */
    @Test
    void aLoneLeadCannotBeMovedOut() {
        TeamCard alone = team(underTest.move(toPool(SECOND_YEAR_MEMBER, team(underTest.board(), TEAM), null), admin),
                TEAM);

        assertRefused(BoardConflict.LEAD_NEEDS_SUCCESSOR, () -> underTest.move(toPool(LEAD, alone, null), admin));
    }

    @Test
    void theLeadCanBeChangedInPlace() {
        TeamCard team = team(underTest.board(), TEAM);

        TeamCard updated = team(underTest.changeLead(TEAM, new BoardLeadRequest(team.version(), SECOND_YEAR_MEMBER),
                admin), TEAM);

        Assertions.assertEquals(SECOND_YEAR_MEMBER, updated.leadId());
        Assertions.assertEquals(Set.of(SECOND_YEAR_MEMBER), leads(updated));
    }

    // --- роспуск ---

    @Test
    void dissolvingSendsEveryMemberToThePool() {
        TeamCard team = team(underTest.board(), TEAM);

        CompositionBoardDto board = underTest.dissolve(TEAM, new BoardVersionRequest(team.version()), admin);

        Assertions.assertTrue(board.teams().stream().noneMatch(card -> card.id() == TEAM));
        Assertions.assertTrue(ids(board.pool()).containsAll(Set.of(LEAD, SECOND_YEAR_MEMBER)));
        Assertions.assertTrue(board.pool().stream().noneMatch(StudentCard::lead));
    }

    // --- конкурентная правка ---

    @Test
    void aStaleVersionOfTheSourceTeamIsAConflictAndChangesNothing() {
        TeamCard team = team(underTest.board(), TEAM);
        BoardMoveRequest stale = new BoardMoveRequest(
                SECOND_YEAR_MEMBER, TEAM, team.version() - 1, null, null, false, null);

        assertRefused(BoardConflict.STALE_VERSION, () -> underTest.move(stale, admin));

        Assertions.assertEquals(Set.of(LEAD, SECOND_YEAR_MEMBER), ids(team(underTest.board(), TEAM).members()));
    }

    @Test
    @Sql("/sql-scripts/board_second_team.sql")
    void aStaleVersionOfTheTargetTeamIsAConflictAndChangesNothing() {
        CompositionBoardDto before = underTest.board();
        TeamCard from = team(before, SECOND_TEAM);
        TeamCard to = team(before, TEAM);
        BoardMoveRequest stale = new BoardMoveRequest(
                6L, from.id(), from.version(), to.id(), to.version() + 1, false, null);

        assertRefused(BoardConflict.STALE_VERSION, () -> underTest.move(stale, admin));

        Assertions.assertEquals(Set.of(4L, 6L), ids(team(underTest.board(), SECOND_TEAM).members()));
    }

    @Test
    void everyOtherBoardActionChecksTheVersionToo() {
        long stale = team(underTest.board(), TEAM).version() + 1;

        assertRefused(BoardConflict.STALE_VERSION,
                () -> underTest.setTargets(TEAM, new BoardTargetsRequest(stale, 1, 1)));
        assertRefused(BoardConflict.STALE_VERSION,
                () -> underTest.changeLead(TEAM, new BoardLeadRequest(stale, SECOND_YEAR_MEMBER), admin));
        assertRefused(BoardConflict.STALE_VERSION,
                () -> underTest.dissolve(TEAM, new BoardVersionRequest(stale), admin));
    }
}
