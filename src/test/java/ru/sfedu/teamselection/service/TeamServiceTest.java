package ru.sfedu.teamselection.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.BasicTestContainerTest;
import ru.sfedu.teamselection.SelectionWindowFixture;
import ru.sfedu.teamselection.TeamSelectionApplication;
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.Team;
import ru.sfedu.teamselection.domain.TeamComposition;
import ru.sfedu.teamselection.domain.Technology;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.dto.ProjectTypeDto;
import ru.sfedu.teamselection.dto.TechnologyDto;
import ru.sfedu.teamselection.dto.team.TeamCreationDto;
import ru.sfedu.teamselection.dto.team.TeamSearchOptionsDto;
import ru.sfedu.teamselection.dto.team.TeamUpdateDto;
import ru.sfedu.teamselection.enums.ApplicationStatus;
import ru.sfedu.teamselection.exception.BusinessException;
import ru.sfedu.teamselection.exception.ConstraintViolationException;
import ru.sfedu.teamselection.exception.ForbiddenException;
import ru.sfedu.teamselection.exception.NotFoundException;
import ru.sfedu.teamselection.repository.ApplicationRepository;
import ru.sfedu.teamselection.repository.StudentRepository;
import ru.sfedu.teamselection.repository.TeamRepository;
import ru.sfedu.teamselection.repository.TrackRepository;


@SpringBootTest(classes = TeamSelectionApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
@ActiveProfiles("test")
@TestPropertySource("/application-test.yml")
@Sql(value = {"/sql-scripts/create_team_for_history.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class TeamServiceTest extends BasicTestContainerTest {
    @Autowired
    private TeamService underTest;
    @Autowired
    private UserService userService;

    @Autowired
    @MockitoSpyBean
    private TeamRepository teamRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private TrackRepository trackRepository;

    private User getAdmin() {
        return userService.findByIdOrElseThrow(1L);
    }


    @BeforeEach
    public void beforeEach() {
        MockitoAnnotations.openMocks(this);
        Mockito.doNothing().when(teamRepository).delete(Mockito.notNull(Team.class));
        openTheSelectionWindow();
    }

    /**
     * Эти тесты про правила команд, а не про окно набора: сид-набор закрылся в прошлом, поэтому окно
     * открывается явно. Само окно разобрано в SelectionWindowServiceTest, а тесты ниже закрывают его
     * намеренно.
     */
    private void openTheSelectionWindow() {
        SelectionWindowFixture.open(trackRepository);
    }

    @Test
    void findByIdOrElseThrow() {
        Team expected = teamRepository.findById(1L).orElseThrow();

        Team actual = underTest.findByIdOrElseThrow(1L);

        Assertions.assertEquals(expected.getId(), actual.getId());
        Assertions.assertEquals(expected.getName(), actual.getName());
        Assertions.assertEquals(expected.getProjectDescription(), actual.getProjectDescription());
        Assertions.assertEquals(expected.getStudents().size(), actual.getStudents().size());
    }

    @Test
    void findAll() {
        List<Team> expected = teamRepository.findAll();

        List<Team> actual = underTest.findAll();

        Assertions.assertEquals(expected.size(), actual.size());
    }

    @Test
    void createWhenCaptainHasTeamShouldFail() {
        TeamCreationDto teamCreationDto = TeamCreationDto.builder()
                .name("shouldFail")
                .projectDescription("projectDescription")
                .projectType(new ProjectTypeDto().id(1L))
                .captainId(2L)
                .currentTrackId(1L)
                .build();

        Assertions.assertThrows(RuntimeException.class, () -> underTest.create(teamCreationDto, getAdmin()));
    }

    @Test
    void create() {
        TeamCreationDto teamCreationDto = TeamCreationDto.builder()
                .name("new")
                .projectDescription("projectDescription")
                .projectType(new ProjectTypeDto().id(1L))
                .captainId(5L)
                .currentTrackId(1L)
                .build();

        Team actual = underTest.create(teamCreationDto, getAdmin());

        Assertions.assertEquals(teamCreationDto.getName(), actual.getName());
        Assertions.assertEquals(teamCreationDto.getProjectDescription(), actual.getProjectDescription());
        Assertions.assertEquals(teamCreationDto.getCaptainId(), actual.getCaptainId());
        Assertions.assertEquals(1, actual.getStudents().size());
    }

    @Test
    void createIgnoresClientTrackAndUsesTheCurrentSelection() {
        TeamCreationDto teamCreationDto = TeamCreationDto.builder()
                .name("history attempt")
                .projectType(new ProjectTypeDto().id(1L))
                .captainId(5L)
                .currentTrackId(2L)
                .build();

        Team actual = underTest.create(teamCreationDto, getAdmin());

        Assertions.assertEquals(1L, actual.getCurrentTrack().getId());
    }

    @Test
    @Sql(statements = "UPDATE students SET current_track_id = 2 WHERE id = 9")
    void createWhenCaptainIsNotRegisteredForTheCurrentSelectionShouldFail() {
        TeamCreationDto teamCreationDto = TeamCreationDto.builder()
                .name("stale captain")
                .projectType(new ProjectTypeDto().id(1L))
                .captainId(9L)
                .build();

        Assertions.assertThrows(BusinessException.class, () -> underTest.create(teamCreationDto, getAdmin()));
    }

    @Test
    void createOnExistingTeamShouldFail() {
        TeamCreationDto teamCreationDto = TeamCreationDto.builder()
                .name("Almost full")
                .projectDescription("new projectDescription")
                .projectType(new ProjectTypeDto().id(2L))
                .captainId(5L)
                .currentTrackId(1L)
                .build();

        Assertions.assertThrows(BusinessException.class, ()-> underTest.create(teamCreationDto, getAdmin()));
    }

    @Test
    void delete() {
        Long teamId = 1L;
        Team team = teamRepository.findById(teamId).orElseThrow();
        List<Student> students =  team.getStudents();

        underTest.delete(teamId);
        for (Student student: students) {
            // current team has not been removed -> it must not be equal to the deleted team
            if (student.getCurrentTeam() != null) {
                Assertions.assertNotEquals(teamId, student.getCurrentTeam().getId());
            } else {
                // current team has been removed -> corresponding field must have been updated
                Assertions.assertEquals(false, student.getHasTeam());
                Assertions.assertEquals(false, student.getIsCaptain());
            }
        }
    }

    @Test
    void updateFromTeamCaptain() {
        Team beforeUpdateTeam = teamRepository.findById(1L).orElseThrow();
        int membersBefore = beforeUpdateTeam.getStudents().size();

        TeamUpdateDto teamDto = TeamUpdateDto.builder()
                .name(beforeUpdateTeam.getName())
                .projectDescription("contacts")
                .projectType(new ProjectTypeDto().id(1L))
                .build();

        Team actual = underTest.update(
                beforeUpdateTeam.getId(),
                teamDto,
                userService.findByIdOrElseThrow(
                        studentRepository.findById(beforeUpdateTeam.getCaptainId()).orElseThrow().getUser().getId()
                )
        );
        Assertions.assertEquals(teamDto.getProjectDescription(), actual.getProjectDescription());
        Assertions.assertEquals(teamDto.getProjectType().getId(), actual.getProjectType().getId());
        Assertions.assertEquals(1L, actual.getCurrentTrack().getId());
        Assertions.assertEquals(membersBefore, actual.getStudents().size());
    }

    @Test
    void updateFromAdmin() {
        Team beforeUpdateTeam = teamRepository.findById(1L).orElseThrow();
        int membersBefore = beforeUpdateTeam.getStudents().size();
        Long captainBefore = beforeUpdateTeam.getCaptainId();

        TeamUpdateDto teamDto = TeamUpdateDto.builder()
                .name(beforeUpdateTeam.getName())
                .projectDescription("contacts")
                .projectType(new ProjectTypeDto().id(3L))
                .build();

        Team actual = underTest.update(
                beforeUpdateTeam.getId(),
                teamDto,
                userService.findByIdOrElseThrow(1L)
        );

        Assertions.assertEquals(teamDto.getProjectDescription(), actual.getProjectDescription());
        Assertions.assertEquals(teamDto.getProjectType().getId(), actual.getProjectType().getId());
        // капитанство и состав через PUT больше не меняются даже администратором (#9)
        Assertions.assertEquals(captainBefore, actual.getCaptainId());
        Assertions.assertEquals(1L, actual.getCurrentTrack().getId());
        Assertions.assertEquals(membersBefore, actual.getStudents().size());
    }

    @Test
    void updateTeamOfFinishedSelectionShouldFail() {
        Team history = teamRepository.findById(2L).orElseThrow();

        TeamUpdateDto teamDto = TeamUpdateDto.builder()
                .name("renamed")
                .projectType(new ProjectTypeDto().id(1L))
                .build();

        Assertions.assertThrows(BusinessException.class, () -> underTest.update(history.getId(), teamDto, getAdmin()));
    }

    @Test
    void deleteTeamOfFinishedSelectionShouldFail() {
        Assertions.assertThrows(BusinessException.class, () -> underTest.delete(2L));
    }

    @Test
    void updateFromForeignUser() {
        Team beforeUpdateTeam = teamRepository.findById(2L).orElseThrow();

        TeamUpdateDto teamDto = TeamUpdateDto.builder()
                .name("about self") // should not be updated
                .projectDescription("contacts")
                .projectType(new ProjectTypeDto().id(1L))
                .build();

        Assertions.assertThrows(
            ForbiddenException.class,
            () -> underTest.update(
                beforeUpdateTeam.getId(),
                teamDto,
                userService.findByIdOrElseThrow(2L)
            )
        );
    }

    @Test
    void searchByLike() {
        String like = "te";

        Page<Team> actual = underTest.search(
                like,
                null,
                null,
                null,
                null,
                null,
                Pageable.unpaged()
        );

        for (Team team : actual) {
            Assertions.assertTrue(team.getName().toLowerCase().contains(like));
        }

        Assertions.assertEquals(3, actual.getTotalElements());
    }

    @Test
    void searchByTrack() {
        Long trackParam = 2L;

        Page<Team> actual = underTest.search(
                null,
                trackParam,
                null,
                null,
                null,
                null,
                Pageable.unpaged()
        );

        for (Team team : actual) {
            Assertions.assertEquals(trackParam, team.getCurrentTrack().getId());
        }

        Assertions.assertEquals(2, actual.getTotalElements());
    }

    @Test
    void searchByIsFull() {
        Boolean isFullParam = true;

        Page<Team> actual = underTest.search(
                null,
                null,
                isFullParam,
                null,
                null,
                null,
                Pageable.unpaged()
        );

        for (Team team : actual) {
            Assertions.assertEquals(isFullParam, TeamComposition.of(team).complete());
        }

        Assertions.assertEquals(1, actual.getTotalElements());
    }

    @Test
    @Sql(statements = "UPDATE teams SET first_year_target = 4 WHERE id = 1003")
    void searchByIsFullRespectsTeamOverride() {
        Page<Team> actual = underTest.search(null, null, true, null, null, null, Pageable.unpaged());

        Assertions.assertEquals(0, actual.getTotalElements());
    }

    @Test
    void searchByProjectType() {
        var projectTypeParam = List.of("Mobile");

        Page<Team> actual = underTest.search(
                null,
                null,
                null,
                projectTypeParam,
                null,
                null,
                Pageable.unpaged()
        );

        for (Team team : actual) {
            Assertions.assertTrue(projectTypeParam.contains(team.getProjectType().getName()));
        }

        Assertions.assertEquals(2, actual.getTotalElements());
    }

    @Test
    void searchByTechnologies() {
        List<Long> technologiesParam = List.of(2L, 10L, 16L);

        Page<Team> actual = underTest.search(
                null,
                null,
                null,
                null,
                technologiesParam,
                null,
                Pageable.unpaged()
        );

        for (Team team : actual) {
            Assertions.assertTrue(team.getTechnologies()
                    .stream()
                    .map(Technology::getId)
                    .anyMatch(technologiesParam::contains)
            );
        }

        Assertions.assertEquals(4, actual.getTotalElements());
    }

    @Test
    void addStudentToTeamWhoHasTeamShouldFail() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> underTest.addStudentToTeam(
                        2L,
                        2L,
                        studentRepository.findById(2L).orElseThrow().getUser()
                )
        );
    }

    @Test
    void addStudentToFullTeamShouldFail() {
        Assertions.assertThrows(RuntimeException.class,
                () -> underTest.addStudentToTeam(
                        1004L,
                         9L, // first-year places of 1004 are taken
                        studentRepository.findById(9L).orElseThrow().getUser()
                )
        );
    }

    @Test
    void addSecondYearStudentToTeamOverLimitShouldFail() {
        Assertions.assertThrows(RuntimeException.class,
                () -> underTest.addStudentToTeam(1003L,
                        13L,
                        studentRepository.findById(13L).orElseThrow().getUser()
                )
        );
    }

    @Test
    void addStudentToTeamInWhichWasMemberBeforeShouldFail() {
        Assertions.assertThrows(RuntimeException.class,
                () -> underTest.addStudentToTeam(
                        1003L,
                        1L,
                        studentRepository.findById(1L).orElseThrow().getUser()
                )
        );
    }

    @Test
    void addStudentToTeam() {
        Long teamId = 1L;
        Long studentId = 7L;

        underTest.addStudentToTeam(teamId, studentId, studentRepository.findById(studentId).orElseThrow().getUser());

        Student student = studentRepository.findById(studentId).orElseThrow();
        Team team = teamRepository.findById(teamId).orElseThrow();

        Assertions.assertTrue(student.getHasTeam());
        Assertions.assertEquals(teamId, student.getCurrentTeam().getId());
        Assertions.assertEquals(3, team.getStudents().size());
        Assertions.assertEquals(2, TeamComposition.of(team).firstYears());
        Assertions.assertTrue(team.getStudents().contains(student));
    }

    @Test
    @Sql(statements = "UPDATE teams SET first_year_target = 4 WHERE id = 1003")
    void addFirstYearToTeamWithRaisedFirstYearTarget() {
        Team team = underTest.addStudentToTeam(1003L, 9L, studentRepository.findById(9L).orElseThrow().getUser());

        Assertions.assertEquals(4, TeamComposition.of(team).firstYears());
        Assertions.assertTrue(TeamComposition.of(team).complete());
    }

    @Test
    void addStudentToTeamOfFinishedSelectionShouldFail() {
        Assertions.assertThrows(BusinessException.class,
                () -> underTest.addStudentToTeam(2L, 7L, getAdmin())
        );
    }

    @Test
    @Sql(statements = "UPDATE students SET current_track_id = 2 WHERE id = 9")
    void addStudentFromAnotherSelectionShouldFail() {
        Assertions.assertThrows(ConstraintViolationException.class,
                () -> underTest.addStudentToTeam(1L, 9L, getAdmin())
        );
    }

    @Test
    void removeStudentFromTeam() {
        Student deleteStudent = studentRepository.findById(12L).orElseThrow();

        Team teamBeforeDelete = teamRepository.findById(deleteStudent.getCurrentTeam().getId()).orElseThrow();

        Team teamAfterDelete = underTest.removeStudentFromTeam(teamBeforeDelete, deleteStudent);

        Assertions.assertEquals(1, teamAfterDelete.getStudents().size());
        Assertions.assertEquals(0, TeamComposition.of(teamAfterDelete).secondYears());
    }

    @Test
    void getSearchOptionsTeams() {
        TeamSearchOptionsDto actual = underTest.getSearchOptionsTeams(2L);

        // 24 («Mobile») ушла из справочника технологий вместе с остальными типами проекта (V2.10)
        Set<Long> expectedTechnologies = Set.of(4L, 21L, 22L, 10L, 28L, 29L, 47L, 48L);

        Assertions.assertEquals(
                Set.of(1L, 3L, 2L, 4L, 5L, 6L, 7L), // «Other» (8) was dropped by V2.01
                actual.getProjectTypes().stream().map(ProjectTypeDto::getId).collect(Collectors.toUnmodifiableSet())
        );
        Assertions.assertEquals(
                expectedTechnologies,
                actual.getTechnologies().stream().map(TechnologyDto::getId).collect(Collectors.toUnmodifiableSet())
        );
    }

    @Test
    void getTeamHistoryForStudentWithManyTeams() {
        List<Team> actual = underTest.getTeamHistoryForStudent(2L);

        Assertions.assertEquals(2, actual.size());
    }

    @Test
    void getTeamHistoryForStudentWithoutAnyTeam() {
        List<Team> actual = underTest.getTeamHistoryForStudent(9L);

        Assertions.assertEquals(0, actual.size());
    }

    @Test
    @Sql(statements = """
            INSERT INTO applications
                (id, team_id, student_id, status, type)
            VALUES
                (130, 2, 7, 'sent', 'request'),
                (131, 4, 7, 'sent', 'invite'),
                (132, 3, 7, 'rejected', 'request');
            """,
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(statements = """
            DELETE FROM applications
            WHERE id IN (130, 131, 132);
            """,
            executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void addStudentToTeamCancelsTheirPendingApplications() {
        underTest.addStudentToTeam(1L, 7L, getAdmin());

        Assertions.assertEquals(
                ApplicationStatus.CANCELLED,
                applicationRepository.findById(130L).orElseThrow().status()
        );
        Assertions.assertEquals(
                ApplicationStatus.CANCELLED,
                applicationRepository.findById(131L).orElseThrow().status()
        );
        Assertions.assertEquals(
                ApplicationStatus.REJECTED,
                applicationRepository.findById(132L).orElseThrow().status()
        );
    }

    // --- окно набора (#6) ---

    private void closeTheSelectionWindow() {
        SelectionWindowFixture.closed(trackRepository);
    }

    private void delayTheSelectionOpening() {
        SelectionWindowFixture.notOpenYet(trackRepository);
    }

    private User userOfStudent(Long studentId) {
        return userService.findByIdOrElseThrow(
                studentRepository.findById(studentId).orElseThrow().getUser().getId());
    }

    private TeamCreationDto newTeamFromStudentFive(String name) {
        return TeamCreationDto.builder()
                .name(name)
                .projectDescription("projectDescription")
                .projectType(new ProjectTypeDto().id(1L))
                .captainId(5L)
                .currentTrackId(1L)
                .build();
    }

    @Test
    void aStudentCannotCreateATeamBeforeTheSelectionOpens() {
        delayTheSelectionOpening();
        TeamCreationDto dto = newTeamFromStudentFive("too early");

        Assertions.assertThrows(ForbiddenException.class, () -> underTest.create(dto, userOfStudent(5L)));
    }

    @Test
    void aStudentCannotCreateATeamAfterTheSelectionCloses() {
        closeTheSelectionWindow();
        TeamCreationDto dto = newTeamFromStudentFive("too late");

        Assertions.assertThrows(ForbiddenException.class, () -> underTest.create(dto, userOfStudent(5L)));
    }

    @Test
    void anAdminStillCreatesATeamAfterTheSelectionCloses() {
        closeTheSelectionWindow();
        TeamCreationDto dto = newTeamFromStudentFive("admin cleanup");

        Team actual = underTest.create(dto, getAdmin());

        Assertions.assertEquals("admin cleanup", actual.getName());
    }

    @Test
    void aCaptainCannotUpdateTheTeamAfterTheSelectionCloses() {
        Team team = teamRepository.findById(1L).orElseThrow();
        User captain = userOfStudent(team.getCaptainId());
        TeamUpdateDto dto = TeamUpdateDto.builder()
                .name(team.getName())
                .projectDescription("too late")
                .projectType(new ProjectTypeDto().id(1L))
                .build();
        closeTheSelectionWindow();

        Assertions.assertThrows(ForbiddenException.class, () -> underTest.update(team.getId(), dto, captain));
    }

    @Test
    void anAdminStillUpdatesTheTeamAfterTheSelectionCloses() {
        Team team = teamRepository.findById(1L).orElseThrow();
        TeamUpdateDto dto = TeamUpdateDto.builder()
                .name(team.getName())
                .projectDescription("admin cleanup")
                .projectType(new ProjectTypeDto().id(1L))
                .build();
        closeTheSelectionWindow();

        Team actual = underTest.update(team.getId(), dto, getAdmin());

        Assertions.assertEquals("admin cleanup", actual.getProjectDescription());
    }

    @Test
    void aCaptainCannotUpdateTheTeamBeforeTheSelectionOpens() {
        Team team = teamRepository.findById(1L).orElseThrow();
        User captain = userOfStudent(team.getCaptainId());
        TeamUpdateDto dto = TeamUpdateDto.builder()
                .name(team.getName())
                .projectDescription("too early")
                .projectType(new ProjectTypeDto().id(1L))
                .build();
        delayTheSelectionOpening();

        Assertions.assertThrows(ForbiddenException.class, () -> underTest.update(team.getId(), dto, captain));
    }

    // --- операции с составом (#9) ---
    // Команда 1 из сида: тимлид — студент 2 (пользователь 3), участник — студент 12 (пользователь 13);
    // студент 1 (пользователь 2) в команде не состоит. Набор 1 активен.

    private static final Long TEAM = 1L;
    private static final Long CAPTAIN_STUDENT = 2L;
    private static final Long MEMBER_STUDENT = 12L;
    private static final Long OUTSIDER_STUDENT = 1L;

    @Test
    void theCaptainRemovesAMember() {
        Team actual = underTest.removeMember(TEAM, MEMBER_STUDENT, userOfStudent(CAPTAIN_STUDENT));

        Assertions.assertTrue(actual.getStudents().stream().noneMatch(s -> s.getId().equals(MEMBER_STUDENT)));
        Student removed = studentRepository.findById(MEMBER_STUDENT).orElseThrow();
        Assertions.assertFalse(removed.getHasTeam());
        Assertions.assertNull(removed.getCurrentTeam());
    }

    @Test
    void aPlainMemberCannotRemoveAnyone() {
        Assertions.assertThrows(ForbiddenException.class,
                () -> underTest.removeMember(TEAM, CAPTAIN_STUDENT, userOfStudent(MEMBER_STUDENT)));
    }

    @Test
    void anOutsiderCannotRemoveAMember() {
        Assertions.assertThrows(ForbiddenException.class,
                () -> underTest.removeMember(TEAM, MEMBER_STUDENT, userOfStudent(OUTSIDER_STUDENT)));
    }

    @Test
    void anAdminRemovesAMember() {
        Team actual = underTest.removeMember(TEAM, MEMBER_STUDENT, getAdmin());

        Assertions.assertTrue(actual.getStudents().stream().noneMatch(s -> s.getId().equals(MEMBER_STUDENT)));
    }

    @Test
    void theCaptainCannotBeRemoved() {
        Assertions.assertThrows(ConstraintViolationException.class,
                () -> underTest.removeMember(TEAM, CAPTAIN_STUDENT, getAdmin()));
    }

    @Test
    void removingSomeoneWhoIsNotAMemberIsNotFound() {
        Assertions.assertThrows(NotFoundException.class,
                () -> underTest.removeMember(TEAM, OUTSIDER_STUDENT, getAdmin()));
    }

    @Test
    void aMemberLeavesTheTeam() {
        Team actual = underTest.leave(TEAM, userOfStudent(MEMBER_STUDENT));

        Assertions.assertTrue(actual.getStudents().stream().noneMatch(s -> s.getId().equals(MEMBER_STUDENT)));
        Assertions.assertFalse(studentRepository.findById(MEMBER_STUDENT).orElseThrow().getHasTeam());
    }

    @Test
    void theCaptainCannotLeaveAndIsToldWhatToDoInstead() {
        ConstraintViolationException refusal = Assertions.assertThrows(ConstraintViolationException.class,
                () -> underTest.leave(TEAM, userOfStudent(CAPTAIN_STUDENT)));

        Assertions.assertTrue(refusal.getMessage().contains("капитанств"), refusal.getMessage());
    }

    @Test
    void someoneWhoIsNotAMemberCannotLeave() {
        Assertions.assertThrows(ForbiddenException.class,
                () -> underTest.leave(TEAM, userOfStudent(OUTSIDER_STUDENT)));
    }

    @Test
    void theCaptainHandsCaptaincyToAMember() {
        Team actual = underTest.transferCaptaincy(TEAM, MEMBER_STUDENT, userOfStudent(CAPTAIN_STUDENT));

        Assertions.assertEquals(MEMBER_STUDENT, actual.getCaptainId());
        Assertions.assertTrue(studentRepository.findById(MEMBER_STUDENT).orElseThrow().getIsCaptain());
        Assertions.assertFalse(studentRepository.findById(CAPTAIN_STUDENT).orElseThrow().getIsCaptain());
    }

    @Test
    void aMemberCannotTakeCaptaincy() {
        Assertions.assertThrows(ForbiddenException.class,
                () -> underTest.transferCaptaincy(TEAM, MEMBER_STUDENT, userOfStudent(MEMBER_STUDENT)));
    }

    @Test
    void anAdminHandsCaptaincyOver() {
        Team actual = underTest.transferCaptaincy(TEAM, MEMBER_STUDENT, getAdmin());

        Assertions.assertEquals(MEMBER_STUDENT, actual.getCaptainId());
    }

    @Test
    void captaincyCannotGoToSomeoneOutsideTheTeam() {
        Assertions.assertThrows(NotFoundException.class,
                () -> underTest.transferCaptaincy(TEAM, OUTSIDER_STUDENT, getAdmin()));
    }

    @Test
    void captaincyCannotGoToTheCurrentCaptain() {
        Assertions.assertThrows(ConstraintViolationException.class,
                () -> underTest.transferCaptaincy(TEAM, CAPTAIN_STUDENT, getAdmin()));
    }

    @Test
    void theCaptainDisbandsTheTeam() {
        underTest.disband(TEAM, userOfStudent(CAPTAIN_STUDENT));

        Assertions.assertTrue(teamRepository.findById(TEAM).isEmpty());
        Student formerMember = studentRepository.findById(MEMBER_STUDENT).orElseThrow();
        Assertions.assertFalse(formerMember.getHasTeam());
        Assertions.assertNull(formerMember.getCurrentTeam());
    }

    @Test
    void aMemberCannotDisbandTheTeam() {
        Assertions.assertThrows(ForbiddenException.class,
                () -> underTest.disband(TEAM, userOfStudent(MEMBER_STUDENT)));
    }

    @Test
    void anAdminDisbandsTheTeam() {
        underTest.disband(TEAM, getAdmin());

        Assertions.assertTrue(teamRepository.findById(TEAM).isEmpty());
    }

    // --- те же операции против закрытого окна (#6) ---

    @Test
    void removingAMemberIsRefusedAfterTheSelectionCloses() {
        closeTheSelectionWindow();

        Assertions.assertThrows(ForbiddenException.class,
                () -> underTest.removeMember(TEAM, MEMBER_STUDENT, userOfStudent(CAPTAIN_STUDENT)));
    }

    @Test
    void leavingIsRefusedAfterTheSelectionCloses() {
        closeTheSelectionWindow();

        Assertions.assertThrows(ForbiddenException.class,
                () -> underTest.leave(TEAM, userOfStudent(MEMBER_STUDENT)));
    }

    @Test
    void transferringCaptaincyIsRefusedAfterTheSelectionCloses() {
        closeTheSelectionWindow();

        Assertions.assertThrows(ForbiddenException.class,
                () -> underTest.transferCaptaincy(TEAM, MEMBER_STUDENT, userOfStudent(CAPTAIN_STUDENT)));
    }

    @Test
    void disbandingIsRefusedAfterTheSelectionCloses() {
        closeTheSelectionWindow();

        Assertions.assertThrows(ForbiddenException.class,
                () -> underTest.disband(TEAM, userOfStudent(CAPTAIN_STUDENT)));
    }

    // Для leave админского варианта нет: администратор не состоит в команде, и после закрытия он
    // получил бы отказ «вы не состоите в этой команде», а не подтверждение освобождения от окна.

    @Test
    void anAdminStillRemovesAMemberAfterTheSelectionCloses() {
        closeTheSelectionWindow();

        Team actual = underTest.removeMember(TEAM, MEMBER_STUDENT, getAdmin());

        Assertions.assertTrue(actual.getStudents().stream().noneMatch(s -> s.getId().equals(MEMBER_STUDENT)));
    }

    @Test
    void anAdminStillHandsCaptaincyOverAfterTheSelectionCloses() {
        closeTheSelectionWindow();

        Assertions.assertEquals(MEMBER_STUDENT, underTest.transferCaptaincy(TEAM, MEMBER_STUDENT, getAdmin()).getCaptainId());
    }

    @Test
    void anAdminStillDisbandsTheTeamAfterTheSelectionCloses() {
        closeTheSelectionWindow();

        underTest.disband(TEAM, getAdmin());

        Assertions.assertTrue(teamRepository.findById(TEAM).isEmpty());
    }

    // Команда 3 из сида живёт в завершённом наборе 3: тимлид — студент 17, участники 8, 14, 15, 16.
    private static final Long ARCHIVED_TEAM = 3L;
    private static final Long ARCHIVED_TEAM_CAPTAIN = 17L;
    private static final Long ARCHIVED_TEAM_MEMBER = 8L;

    @Test
    void captaincyCannotBeHandedOverInAFinishedSelection() {
        Assertions.assertThrows(BusinessException.class,
                () -> underTest.transferCaptaincy(
                        ARCHIVED_TEAM, ARCHIVED_TEAM_MEMBER, userOfStudent(ARCHIVED_TEAM_CAPTAIN)));
    }

    @Test
    void anOutsiderCannotTransferCaptaincy() {
        Assertions.assertThrows(ForbiddenException.class,
                () -> underTest.transferCaptaincy(TEAM, MEMBER_STUDENT, userOfStudent(OUTSIDER_STUDENT)));
    }

    @Test
    void anOutsiderCannotDisbandTheTeam() {
        Assertions.assertThrows(ForbiddenException.class,
                () -> underTest.disband(TEAM, userOfStudent(OUTSIDER_STUDENT)));
    }
}
