package ru.sfedu.teamselection.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.BasicTestContainerTest;
import ru.sfedu.teamselection.SelectionWindowFixture;
import ru.sfedu.teamselection.TeamSelectionApplication;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.domain.application.ApplicationType;
import ru.sfedu.teamselection.dto.StudentUpdateDto;
import ru.sfedu.teamselection.dto.StudentUpdateTrackDto;
import ru.sfedu.teamselection.dto.StudentUpdateUserDto;
import ru.sfedu.teamselection.dto.UserDto;
import ru.sfedu.teamselection.dto.application.ApplicationCreationDto;
import ru.sfedu.teamselection.dto.board.BoardMoveRequest;
import ru.sfedu.teamselection.dto.student.StudentCreationDto;
import ru.sfedu.teamselection.dto.track.NewSelectionDto;
import ru.sfedu.teamselection.dto.track.TrackDto;
import ru.sfedu.teamselection.enums.ApplicationStatus;
import ru.sfedu.teamselection.enums.ConflictReason;
import ru.sfedu.teamselection.exception.ConflictException;
import ru.sfedu.teamselection.repository.StudentRepository;
import ru.sfedu.teamselection.repository.TeamRepository;
import ru.sfedu.teamselection.repository.TrackRepository;
import ru.sfedu.teamselection.repository.UserRepository;
import ru.sfedu.teamselection.service.security.PermissionLevelUpdate;

/**
 * Набор, переданный в кабинет ПД (#15), только для чтения — для студентов, тимлидов и
 * администраторов. Окно сид-набора закрыто, как и будет к моменту передачи: студент должен
 * получить «передано», а не «окно закрыто».
 *
 * <p>Сид: команда 1 — тимлид студент 2 (пользователь 3), участник студент 12 (пользователь 13);
 * студент 4 (пользователь 5) свободен; у студента 5 (пользователь 6) висит заявка 5 в команду 1.
 */
@SpringBootTest(classes = TeamSelectionApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
@ActiveProfiles("test")
@TestPropertySource("/application-test.yml")
class HandOverLockTest extends BasicTestContainerTest {

    private static final long TEAM = 1L;

    @Autowired
    private TrackService trackService;
    @Autowired
    private TeamService teamService;
    @Autowired
    private TeamJoinLinkService teamJoinLinkService;
    @Autowired
    private ApplicationService applicationService;
    @Autowired
    private StudentService studentService;
    @Autowired
    private CompositionBoardService boardService;
    @Autowired
    private UserService userService;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private UserRepository userRepository;

    private Track track;
    private User admin;

    @BeforeEach
    void handOverTheClosedSelection() {
        SelectionWindowFixture.closed(trackRepository);
        admin = userRepository.findById(1L).orElseThrow();
        track = trackService.handOver(trackRepository.findByActiveTrue().orElseThrow().getId(), admin);
    }

    private User user(long id) {
        return userRepository.findById(id).orElseThrow();
    }

    private static void assertHandedOver(Executable action) {
        ConflictException refusal = Assertions.assertThrows(ConflictException.class, action);
        Assertions.assertEquals(ConflictReason.HANDED_OVER, refusal.getCode());
    }

    private static StudentCreationDto questionnaireOfUser5() {
        return StudentCreationDto.builder().course(1).groupNumber(1).contacts("tg").userId(5L).build();
    }

    // --- студенты и тимлиды ---

    @Test
    void aMemberLeavingGetsHandedOverRatherThanWindowClosed() {
        assertHandedOver(() -> teamService.leave(TEAM, user(13)));
    }

    @Test
    void aStudentCannotApply() {
        ApplicationCreationDto request = ApplicationCreationDto.builder()
                .status(ApplicationStatus.SENT)
                .studentId(4L)
                .teamId(TEAM)
                .type(ApplicationType.REQUEST)
                .build();

        assertHandedOver(() -> applicationService.create(request, user(5)));
    }

    @Test
    void theQuestionnaireCannotBeFilledOrChanged() {
        assertHandedOver(() -> studentService.create(questionnaireOfUser5(), user(5)));
    }

    /** Правка пользователя заодно правит его анкету — а ФИО, почта и анкета уже в составе core. */
    @Test
    void aStudentCannotEditTheirAccountEither() {
        UserDto self = UserDto.builder().id(13L).fio("Новое имя").email("new@sfedu.ru").build();

        assertHandedOver(() -> userService.createOrUpdate(self, PermissionLevelUpdate.OWNER));
    }

    @Test
    void aLeadCannotEvenDisableTheJoinLink() {
        assertHandedOver(() -> teamJoinLinkService.disable(TEAM, user(3)));
    }

    // --- администратор ---

    @Test
    void anAdminCannotMoveStudentsOnTheBoard() {
        long version = teamRepository.findById(TEAM).orElseThrow().getVersion();
        BoardMoveRequest move = new BoardMoveRequest(4L, null, null, TEAM, version, true, null);

        assertHandedOver(() -> boardService.move(move, admin));
    }

    @Test
    void anAdminCannotDeleteATeam() {
        assertHandedOver(() -> teamService.delete(TEAM, admin));
    }

    @Test
    void anAdminCannotEditOrDeleteAStudent() {
        assertHandedOver(() -> studentService.update(12L, new StudentUpdateDto(), PermissionLevelUpdate.ADMIN, admin));
        assertHandedOver(() -> studentService.delete(4L, admin));
    }

    /** Студент 3 — из другого, незапертого набора; перевести его в переданный набор тоже нельзя. */
    @Test
    void anAdminCannotMoveAStudentIntoTheHandedOverSelection() {
        StudentUpdateDto intoTheHandedOver = new StudentUpdateDto()
                .course(1)
                .currentTrack(new StudentUpdateTrackDto().id(track.getId()))
                .user(new StudentUpdateUserDto().fio("Студент 3").email("s3@sfedu.ru")
                        .isEnabled(true).isRemindEnabled(true));

        assertHandedOver(() -> studentService.update(3L, intoTheHandedOver, PermissionLevelUpdate.ADMIN, admin));
    }

    @Test
    void anAdminCannotDeleteAnApplication() {
        assertHandedOver(() -> applicationService.delete(5L));
    }

    @Test
    void theSelectionSettingsAreLockedToo() {
        TrackDto settings = TrackDto.builder()
                .name(track.getName())
                .type(track.getType().name())
                .startDate(track.getStartDate())
                .endDate(track.getEndDate())
                .build();

        assertHandedOver(() -> trackService.update(track.getId(), settings, admin));
    }

    // --- что остаётся возможным ---

    @Test
    void theNextSelectionCanStillBeStartedAndJoined() {
        Track next = trackService.startNewSelection(NewSelectionDto.builder()
                .name("Следующий набор")
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(10))
                .build(), admin);

        Assertions.assertNull(next.getHandedOverAt());
        studentService.create(questionnaireOfUser5(), user(5));
        Assertions.assertEquals(next.getId(), studentRepository.findById(4L).orElseThrow().getCurrentTrack().getId());
    }

    /** Core может повторить вызов, если ответ потерялся: время передачи остаётся первым. */
    @Test
    void handingOverAgainKeepsTheFirstTime() {
        LocalDateTime first = track.getHandedOverAt();

        Assertions.assertNotNull(first);
        Assertions.assertEquals(first, trackService.handOver(track.getId(), admin).getHandedOverAt());
    }

    @Test
    void cancellingTheHandOverUnlocksTheSelection() {
        trackService.cancelHandOver(track.getId(), admin);

        Assertions.assertNull(trackRepository.findById(track.getId()).orElseThrow().getHandedOverAt());
        Assertions.assertDoesNotThrow(() -> studentService.delete(4L, admin));
    }
}
