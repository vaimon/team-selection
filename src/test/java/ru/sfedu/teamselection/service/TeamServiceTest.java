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
import ru.sfedu.teamselection.exception.BusinessException;
import ru.sfedu.teamselection.exception.ConstraintViolationException;
import ru.sfedu.teamselection.exception.ForbiddenException;
import ru.sfedu.teamselection.repository.StudentRepository;
import ru.sfedu.teamselection.repository.TeamRepository;


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

    private User getAdmin() {
        return userService.findByIdOrElseThrow(1L);
    }


    @BeforeEach
    public void beforeEach() {
        MockitoAnnotations.openMocks(this);
        Mockito.doNothing().when(teamRepository).delete(Mockito.notNull(Team.class));
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
                .id(beforeUpdateTeam.getId())
                .captainId(beforeUpdateTeam.getCaptainId())
                .name(beforeUpdateTeam.getName())
                .projectDescription("contacts")
                .projectType(new ProjectTypeDto().id(1L))
                .studentIds(beforeUpdateTeam.getStudents().stream().map(Student::getId).collect(Collectors.toUnmodifiableSet()))
                .currentTrackId(2L) // ignored: a team never moves between selections
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

        TeamUpdateDto teamDto = TeamUpdateDto.builder()
                .id(beforeUpdateTeam.getId())
                .name(beforeUpdateTeam.getName())
                .projectDescription("contacts")
                .projectType(new ProjectTypeDto().id(3L))
                .captainId(12L) // should be updated
                .studentIds(beforeUpdateTeam.getStudents().stream().map(Student::getId).collect(Collectors.toUnmodifiableSet()))
                .currentTrackId(2L) // ignored
                .build();

        Team actual = underTest.update(
                beforeUpdateTeam.getId(),
                teamDto,
                userService.findByIdOrElseThrow(1L)
        );

        Assertions.assertEquals(teamDto.getProjectDescription(), actual.getProjectDescription());
        Assertions.assertEquals(teamDto.getProjectType().getId(), actual.getProjectType().getId());
        Assertions.assertEquals(12L, actual.getCaptainId());
        Assertions.assertEquals(1L, actual.getCurrentTrack().getId());
        Assertions.assertEquals(membersBefore, actual.getStudents().size());
    }

    @Test
    void updateTeamOfFinishedSelectionShouldFail() {
        Team history = teamRepository.findById(2L).orElseThrow();

        TeamUpdateDto teamDto = TeamUpdateDto.builder()
                .id(history.getId())
                .name("renamed")
                .projectType(new ProjectTypeDto().id(1L))
                .captainId(history.getCaptainId())
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
                .id(beforeUpdateTeam.getId())
                .name("about self") // should not be updated
                .projectDescription("contacts")
                .projectType(new ProjectTypeDto().id(1L))
                .currentTrackId(beforeUpdateTeam.getCurrentTrack().getId())
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
        Page<Team> actual = underTest.search(null, null, true, null, null, Pageable.unpaged());

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

        Set<Long> expectedTechnologies = Set.of(4L, 21L, 22L, 24L, 10L, 28L, 29L, 47L, 48L);

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
}