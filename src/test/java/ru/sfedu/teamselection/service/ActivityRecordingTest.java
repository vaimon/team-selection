package ru.sfedu.teamselection.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.BasicTestContainerTest;
import ru.sfedu.teamselection.SelectionWindowFixture;
import ru.sfedu.teamselection.TeamSelectionApplication;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.domain.activity.ActivityEntry;
import ru.sfedu.teamselection.domain.application.ApplicationType;
import ru.sfedu.teamselection.dto.UserDto;
import ru.sfedu.teamselection.dto.application.ApplicationCreationDto;
import ru.sfedu.teamselection.dto.board.BoardMoveRequest;
import ru.sfedu.teamselection.dto.student.StudentCreationDto;
import ru.sfedu.teamselection.dto.track.NewSelectionDto;
import ru.sfedu.teamselection.dto.track.TrackDto;
import ru.sfedu.teamselection.enums.ActivityAction;
import ru.sfedu.teamselection.enums.ApplicationStatus;
import ru.sfedu.teamselection.repository.ActivityRepository;
import ru.sfedu.teamselection.repository.TeamRepository;
import ru.sfedu.teamselection.repository.TrackRepository;
import ru.sfedu.teamselection.repository.UserRepository;
import ru.sfedu.teamselection.service.security.PermissionLevelUpdate;

/**
 * Действия действительно попадают в историю (#16) — по представителю на каждый путь записи.
 * Полный список ручек стережёт ActivityCoverageTest.
 *
 * <p>Сид: команда 1 — тимлид студент 2 (пользователь 3), участник студент 12 (пользователь 13);
 * студент 4 (пользователь 5) свободен.
 */
@SpringBootTest(classes = TeamSelectionApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
@ActiveProfiles("test")
@TestPropertySource("/application-test.yml")
class ActivityRecordingTest extends BasicTestContainerTest {

    private static final long TEAM = 1L;

    @Autowired
    private TeamService teamService;
    @Autowired
    private CompositionBoardService boardService;
    @Autowired
    private ApplicationService applicationService;
    @Autowired
    private StudentService studentService;
    @Autowired
    private TrackService trackService;
    @Autowired
    private UserService userService;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User admin;

    @BeforeEach
    void openTheWindow() {
        SelectionWindowFixture.open(trackRepository);
        admin = userRepository.findById(1L).orElseThrow();
        activityRepository.deleteAll();
    }

    private User user(long id) {
        return userRepository.findById(id).orElseThrow();
    }

    private List<ActivityEntry> entries(ActivityAction action) {
        return activityRepository.findAll().stream()
                .filter(entry -> entry.getAction() == action)
                .toList();
    }

    private ActivityEntry only(ActivityAction action) {
        List<ActivityEntry> found = entries(action);
        Assertions.assertEquals(1, found.size(), "ожидалась одна запись " + action + ", а есть " + found.size());
        return found.get(0);
    }

    @Test
    void aMemberLeavingIsRecordedWithThemAsTheActor() {
        teamService.leave(TEAM, user(13));

        ActivityEntry entry = only(ActivityAction.MEMBER_LEFT);
        Assertions.assertEquals(12L, entry.getStudentId());
        Assertions.assertEquals(TEAM, entry.getTeamId());
        Assertions.assertEquals(13L, entry.getActorUserId());
    }

    @Test
    void aLeadRemovingAMemberIsRecorded() {
        teamService.removeMember(TEAM, 12L, user(3));

        Assertions.assertEquals(3L, only(ActivityAction.MEMBER_REMOVED).getActorUserId());
    }

    /** Перемещение по доске — одна запись, а не «исключён» плюс «добавлен». */
    @Test
    void aBoardMoveIsRecordedOnce() {
        long version = teamRepository.findById(TEAM).orElseThrow().getVersion();

        boardService.move(new BoardMoveRequest(4L, null, null, TEAM, version, true, null), admin);

        Assertions.assertEquals(1, entries(ActivityAction.MEMBER_MOVED).size());
        Assertions.assertEquals(0, entries(ActivityAction.MEMBER_JOINED).size());
        Assertions.assertEquals(0, entries(ActivityAction.MEMBER_REMOVED).size());
    }

    /**
     * Перемещение тимлида — два разных факта: у команды новый тимлид, а прежний ушёл. Обе записи
     * нужны: по первой видно, кто теперь отвечает за команду, по второй — куда делся человек.
     */
    @Test
    void movingTheLeadRecordsBothTheHandOverAndTheMove() {
        long version = teamRepository.findById(TEAM).orElseThrow().getVersion();

        boardService.move(new BoardMoveRequest(2L, TEAM, version, null, null, false, 12L), admin);

        Assertions.assertEquals(12L, only(ActivityAction.LEAD_CHANGED).getStudentId());
        Assertions.assertEquals(2L, only(ActivityAction.MEMBER_MOVED).getStudentId());
    }

    /** Роспуск с доски идёт через ту же операцию, что и у тимлида: запись одна и та же. */
    @Test
    void disbandingIsRecordedOnceWhereverItStarted() {
        teamService.disband(TEAM, user(3));

        Assertions.assertEquals(TEAM, only(ActivityAction.TEAM_DISBANDED).getTeamId());
    }

    @Test
    void anApplicationAndItsAnswerAreBothRecorded() {
        ApplicationCreationDto request = ApplicationCreationDto.builder()
                .status(ApplicationStatus.SENT)
                .studentId(4L)
                .teamId(TEAM)
                .type(ApplicationType.REQUEST)
                .build();
        var created = applicationService.create(request, user(5));

        applicationService.update(ApplicationCreationDto.builder()
                .id(created.getId())
                .status(ApplicationStatus.REJECTED)
                .studentId(4L)
                .teamId(TEAM)
                .type(ApplicationType.REQUEST)
                .build(), user(3));

        Assertions.assertEquals(5L, only(ActivityAction.APPLICATION_SENT).getActorUserId());
        Assertions.assertTrue(only(ActivityAction.APPLICATION_ANSWERED).getSummary().contains("отклонена"));
    }

    @Test
    void theQuestionnaireIsRecorded() {
        studentService.create(
                StudentCreationDto.builder().course(1).groupNumber(1).contacts("tg").userId(5L).build(), user(5));

        Assertions.assertEquals(4L, only(ActivityAction.QUESTIONNAIRE_FILLED).getStudentId());
    }

    /**
     * Правка профиля администратором всегда идёт через выдачу роли: если записывать её безусловно,
     * настоящая смена роли утонет в «роль STUDENT» с каждого сохранения чужого профиля.
     */
    @Test
    void savingAProfileWithoutChangingTheRoleIsNotARoleChange() {
        UserDto sameRole = UserDto.builder()
                .id(13L).fio("Новое имя").email("member@sfedu.ru").role("STUDENT").isEnabled(true).build();

        userService.createOrUpdate(sameRole, PermissionLevelUpdate.ADMIN, admin);

        Assertions.assertEquals(0, entries(ActivityAction.ROLE_ASSIGNED).size());
        Assertions.assertEquals(1, entries(ActivityAction.STUDENT_UPDATED).size());
    }

    @Test
    void aRealRoleChangeIsRecorded() {
        UserDto promoted = UserDto.builder()
                .id(13L).fio("Участник").email("member@sfedu.ru").role("ADMIN").isEnabled(true).build();

        userService.createOrUpdate(promoted, PermissionLevelUpdate.ADMIN, admin);

        Assertions.assertTrue(only(ActivityAction.ROLE_ASSIGNED).getSummary().contains("ADMIN"));
    }

    /** У администратора анкеты нет — но правка его аккаунта тоже должна быть видна в истории. */
    @Test
    void editingAnAccountWithoutAQuestionnaireIsRecordedToo() {
        UserDto renamed = UserDto.builder()
                .id(admin.getId()).fio("Другое имя").email(admin.getEmail()).role("ADMIN").isEnabled(true).build();

        userService.createOrUpdate(renamed, PermissionLevelUpdate.ADMIN, admin);

        Assertions.assertEquals(admin.getId(), only(ActivityAction.USER_UPDATED).getActorUserId());
    }

    /** Тем же PUT администратор заводит аккаунт вручную — это тоже должно быть видно. */
    @Test
    void creatingAnAccountByHandIsRecorded() {
        UserDto fresh = UserDto.builder()
                .fio("Новый человек").email("fresh@sfedu.ru").role("STUDENT").isEnabled(true).build();

        userService.createOrUpdate(fresh, PermissionLevelUpdate.ADMIN, admin);

        Assertions.assertTrue(only(ActivityAction.USER_CREATED).getSummary().contains("Новый человек"));
    }

    @Test
    void selectionSettingsAreRecorded() {
        var active = trackRepository.findByActiveTrue().orElseThrow();

        trackService.update(active.getId(), TrackDto.builder()
                .name(active.getName())
                .type(active.getType().name())
                .startDate(active.getStartDate())
                .endDate(active.getEndDate())
                .build(), admin);

        Assertions.assertEquals(admin.getId(), only(ActivityAction.SELECTION_SETTINGS_CHANGED).getActorUserId());
    }

    /** Передачу отмечает core по ключу интеграции: запись есть, автора у неё нет. */
    @Test
    void aHandOverFromCoreIsRecordedWithoutAnActor() {
        trackService.handOver(trackRepository.findByActiveTrue().orElseThrow().getId(), null);

        Assertions.assertNull(only(ActivityAction.HANDED_OVER).getActorUserId());
    }

    /** Старт набора — тот момент, когда прошлогодние записи и уходят. */
    @Test
    void startingTheNextSelectionRecordsItAndPurgesTheOldEntries() {
        jdbcTemplate.update("INSERT INTO activity_log (action, summary, created_at) VALUES (?, ?, ?)",
                ActivityAction.TEAM_UPDATED.name(), "прошлогодняя запись", LocalDateTime.now().minusDays(500));

        trackService.startNewSelection(NewSelectionDto.builder()
                .name("Набор следующего года")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(30))
                .build(), admin);

        Assertions.assertEquals(1, entries(ActivityAction.SELECTION_STARTED).size());
        Assertions.assertEquals(0, entries(ActivityAction.TEAM_UPDATED).size());
    }
}
