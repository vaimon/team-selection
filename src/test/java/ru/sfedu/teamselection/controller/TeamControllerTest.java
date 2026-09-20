package ru.sfedu.teamselection.controller;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.sfedu.teamselection.config.SecurityConfig;
import ru.sfedu.teamselection.config.security.SimpleAuthenticationSuccessHandler;
import ru.sfedu.teamselection.domain.Role;
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.Team;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.dto.team.TeamSearchOptionsDto;
import ru.sfedu.teamselection.mapper.PageResponseMapper;
import ru.sfedu.teamselection.mapper.student.StudentDtoMapper;
import ru.sfedu.teamselection.mapper.team.TeamDtoMapper;
import ru.sfedu.teamselection.service.ApplicationService;
import ru.sfedu.teamselection.service.TeamExportService;
import ru.sfedu.teamselection.service.TeamJoinLinkService;
import ru.sfedu.teamselection.service.TeamService;
import ru.sfedu.teamselection.service.UserService;
import ru.sfedu.teamselection.service.security.AzureOidcUserService;
import ru.sfedu.teamselection.service.security.CurrentAuthoritiesResolver;
import ru.sfedu.teamselection.service.security.Oauth2UserService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test class for the {@link TeamController}
 */
@ActiveProfiles("test")
@Import(SecurityConfig.class)
@WebMvcTest({TeamController.class})
public class TeamControllerTest {
    @MockitoBean
    private TeamExportService teamExportService;
    @MockitoBean(name = "teamService")
    private TeamService teamService;
    @MockitoBean
    private TeamJoinLinkService teamJoinLinkService;
    @MockitoBean
    private ApplicationService applicationService;
    @MockitoBean
    private UserService userService;

    @MockitoBean
    private SimpleAuthenticationSuccessHandler simpleAuthenticationSuccessHandler;
    @MockitoBean
    private Oauth2UserService oauth2UserService;
    @MockitoBean
    private AzureOidcUserService azureOidcUserService;
    @MockitoBean
    private CurrentAuthoritiesResolver currentAuthoritiesResolver;

    @MockitoBean
    private TeamDtoMapper teamDtoMapper;
    @MockitoBean
    private StudentDtoMapper studentDtoMapper;
    @MockitoBean
    private PageResponseMapper pageResponseMapper;

    @Autowired
    private MockMvc mockMvc;

    private final User admin = User.builder()
            .id(1L)
            .fio("admin")
            .email("admin@.com")
            .isEnabled(true)
            .isRemindEnabled(true)
            .role(Role.builder().id(3L).name("ROLE_ADMIN").build())
            .build();

    // signed in, but no questionnaire for the current selection yet
    private final User studentWithoutQuestionnaire = User.builder()
            .id(5L)
            .fio("New Comer")
            .email("new@sfedu.ru")
            .isEnabled(true)
            .role(Role.builder().id(4L).name("ROLE_STUDENT").build())
            .build();

    private final User genericStudentUser = User.builder()
            .id(2L)
            .fio("A B C")
            .email("example@.com")
            .isEnabled(true)
            .isRemindEnabled(true)
            .role(Role.builder().id(1L).name("ROLE_PARTICIPANT").build()) // filled the questionnaire
            .build();

    private final Student genericStudent = Student.builder()
            .id(666L)
            .course(1)
            .groupNumber(11)
            .aboutSelf("about_self")
            .contacts("dto.getContacts()")
            .currentTeam(Team.builder()
                    .id(1L)
                    .build())
            .user(genericStudentUser)
            .build();

    private final Team genericTeam = Team.builder()
            .build();

    private final List<Team> teams = List.of(
            genericTeam
    );

    @BeforeEach
    public void beforeEach() {
        Mockito.doReturn(genericStudentUser)
                .when(userService).getCurrentUser();
    }

    @Test
    public void getSearchOptionsTeams() throws Exception {
        Mockito.doReturn(TeamSearchOptionsDto.builder().build())
                .when(teamService)
                .getSearchOptionsTeams(Mockito.anyLong());

        mockMvc.perform(get(TeamController.GET_SEARCH_OPTIONS + "?track_id=1")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isOk());
    }

    @Test
    public void findAll() throws Exception {
        Mockito.doReturn(teams).when(teamService).findAll();

        mockMvc.perform(get(TeamController.FIND_ALL)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource(value = {"name,asc", "name,desc", "name"}, delimiter = ';')
    public void search(String sort) throws Exception {
        Mockito.doReturn(new PageImpl<>(teams)).when(teamService).search(
                Mockito.anyString(),
                Mockito.anyLong(),
                Mockito.anyBoolean(),
                Mockito.anyList(),
                Mockito.anyList(),
                Mockito.notNull()
        );

        mockMvc.perform(get(TeamController.SEARCH_TEAMS)
                        .param("input", "")
                        .param("track_id", "0")
                        .param("is_full", "false")
                        .param("project_type", "")
                        .param("technologies", "")
                        .param("sort", sort)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    public void deleteTeamFromNonAdminShouldFail() throws Exception {
        Mockito.doNothing().when(teamService).delete(Mockito.notNull());

        mockMvc.perform(delete(StudentController.DELETE_STUDENT, "1")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().is(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    public void deleteTeam() throws Exception {
        Mockito.doNothing().when(teamService).delete(Mockito.notNull());

        mockMvc.perform(delete(TeamController.DELETE_TEAM, "1")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().is(HttpStatus.NO_CONTENT.value()));
    }

    @Test
    public void deleteTeamNotFromAdminShouldFail() throws Exception {
        Mockito.doNothing().when(teamService).delete(Mockito.notNull());

        mockMvc.perform(delete(TeamController.DELETE_TEAM, "1")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().is(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    public void findById() throws Exception {
        Mockito.doReturn(genericTeam).when(teamService).findByIdOrElseThrow(Mockito.anyLong());

        mockMvc.perform(get(TeamController.FIND_BY_ID, "1")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isOk());
    }

    @Test
    public void findApplicantsById() throws Exception {
        Mockito.doReturn(true).when(teamService).isCurrentUserCaptain(Mockito.anyLong());

        mockMvc.perform(get(TeamController.FIND_APPLICANTS_BY_ID, "1")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    public void createTeam() throws Exception {
        Mockito.doReturn(genericTeam).when(teamService).create(Mockito.notNull(), Mockito.notNull());

        String team = """
                {
                    "name": "",
                    "projectDescription": "",
                    "projectType": {},
                    "captainId": 0,
                    "technologies": [],
                    "currentTrackId": 0
                }""";

        mockMvc.perform(post(TeamController.CREATE_TEAM)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser))
                        .content(team)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    public void addStudentToTeam() throws Exception {
        Mockito.doReturn(genericTeam).when(teamService).addStudentToTeam(
                Mockito.anyLong(),
                Mockito.anyLong(),
                Mockito.any()
        );

        mockMvc.perform(put(TeamController.ADD_STUDENT_TO_TEAM, "1", "1")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    public void addStudentToTeamNotFromAdminShouldFail() throws Exception {
        Mockito.doReturn(genericTeam).when(teamService).addStudentToTeam(
                Mockito.anyLong(),
                Mockito.anyLong(),
                Mockito.any()
        );

        mockMvc.perform(put(TeamController.ADD_STUDENT_TO_TEAM, "1", "1")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isForbidden())
                .andDo(print());
    }

    @Test
    public void updateTeam() throws Exception {
        Mockito.doReturn(genericTeam).when(teamService).update(
                Mockito.anyLong(),
                Mockito.notNull(),
                Mockito.notNull()
        );

        String team = """
                {
                    "id": 1,
                    "name": "name",
                    "project_description": "projectDescription",
                    "project_type": {
                        "id": 1,
                        "name": "name"
                    },
                    "quantity_of_students": 0,
                    "captain_id": 1,
                    "isFull": false,
                    "current_track_id": 0,
                    "students": [],
                    "applications": [],
                    "technologies": []
                }""";

        mockMvc.perform(put(TeamController.UPDATE_TEAM, "1")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser))
                        .content(team)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(print());
    }


    // access matrix, vaimon/team-selection#7

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/api/v1/teams/1", "/api/v1/teams/search", "/api/v1/teams/filters"
    })
    public void studentWithoutQuestionnaireCannotReadTeams(String url) throws Exception {
        mockMvc.perform(get(url)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(studentWithoutQuestionnaire)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void studentWithoutQuestionnaireCannotCreateTeam() throws Exception {
        mockMvc.perform(post(TeamController.CREATE_TEAM)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(studentWithoutQuestionnaire))
                        .content("{\"name\": \"x\", \"captain_id\": 5}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        Mockito.verify(teamService, Mockito.never()).create(Mockito.any(), Mockito.any());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/api/v1/teams", "/api/v1/teams/export/csv?trackId=1", "/api/v1/teams/export/excel?trackId=1"
    })
    public void participantCannotListAllTeamsOrExport(String url) throws Exception {
        mockMvc.perform(get(url)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void onlyTeamLeadOrAdminSeesApplicants() throws Exception {
        Mockito.doReturn(false).when(teamService).isCurrentUserCaptain(1L);

        mockMvc.perform(get(TeamController.FIND_APPLICANTS_BY_ID.replace("{id}", "1"))
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(TeamController.FIND_APPLICANTS_BY_ID.replace("{id}", "1"))
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isOk());
    }

    // --- операции с составом (#9): проверяем маршрутизацию и порядок path-переменных ---

    @Test
    public void removeMemberRoutesTeamAndStudentInThatOrder() throws Exception {
        Mockito.doReturn(genericTeam).when(teamService)
                .removeMember(Mockito.eq(7L), Mockito.eq(42L), Mockito.notNull());

        mockMvc.perform(post("/api/v1/teams/7/members/42/remove")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isOk());

        Mockito.verify(teamService).removeMember(Mockito.eq(7L), Mockito.eq(42L), Mockito.notNull());
    }

    @Test
    public void leaveTeamRoutesTheTeamId() throws Exception {
        Mockito.doReturn(genericTeam).when(teamService).leave(Mockito.eq(7L), Mockito.notNull());

        mockMvc.perform(post("/api/v1/teams/7/leave")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isOk());

        Mockito.verify(teamService).leave(Mockito.eq(7L), Mockito.notNull());
    }

    @Test
    public void transferCaptaincyRoutesTeamAndNewCaptainInThatOrder() throws Exception {
        Mockito.doReturn(genericTeam).when(teamService)
                .transferCaptaincy(Mockito.eq(7L), Mockito.eq(42L), Mockito.notNull());

        mockMvc.perform(post("/api/v1/teams/7/captain/42")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isOk());

        Mockito.verify(teamService).transferCaptaincy(Mockito.eq(7L), Mockito.eq(42L), Mockito.notNull());
    }

    @Test
    public void disbandTeamRoutesTheTeamIdAndReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/teams/7/disband")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isNoContent());

        Mockito.verify(teamService).disband(Mockito.eq(7L), Mockito.notNull());
    }

    @Test
    public void aUserWithoutTheQuestionnaireCannotRunTeamOperations() throws Exception {
        mockMvc.perform(post("/api/v1/teams/7/leave")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .oauth2User(studentWithoutQuestionnaire)))
                .andExpect(status().isForbidden());

        Mockito.verify(teamService, Mockito.never()).leave(Mockito.any(), Mockito.any());
    }

    // --- ссылка-приглашение (#13) ---

    @Test
    public void thePreviewIsReachableBeforeTheQuestionnaireIsFilled() throws Exception {
        Mockito.doReturn(ru.sfedu.teamselection.dto.team.TeamJoinPreviewDto.builder()
                        .teamId(7L)
                        .teamName("Tech Titans")
                        .canJoin(false)
                        .build())
                .when(teamJoinLinkService).preview(Mockito.eq("tok"), Mockito.notNull());

        // ровно тот человек, ради которого превью вынесено из матрицы #7: вошёл, анкеты ещё нет
        mockMvc.perform(get("/api/v1/teams/join/tok")
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .oauth2User(studentWithoutQuestionnaire)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamName").value("Tech Titans"));
    }

    @Test
    public void joiningByLinkStillRequiresTheQuestionnaire() throws Exception {
        mockMvc.perform(post("/api/v1/teams/join/tok")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .oauth2User(studentWithoutQuestionnaire)))
                .andExpect(status().isForbidden());

        Mockito.verify(teamJoinLinkService, Mockito.never()).join(Mockito.any(), Mockito.any());
    }

    @Test
    public void joiningByLinkRoutesTheToken() throws Exception {
        Mockito.doReturn(genericTeam).when(teamJoinLinkService).join(Mockito.eq("tok"), Mockito.notNull());

        mockMvc.perform(post("/api/v1/teams/join/tok")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isOk());

        Mockito.verify(teamJoinLinkService).join(Mockito.eq("tok"), Mockito.notNull());
    }

    @Test
    public void issuingAndDisablingTheLinkRouteTheTeamId() throws Exception {
        Mockito.doReturn("tok").when(teamJoinLinkService).issue(Mockito.eq(7L), Mockito.notNull());

        mockMvc.perform(post("/api/v1/teams/7/join-link")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/teams/7/join-link/disable")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isNoContent());

        Mockito.verify(teamJoinLinkService).issue(Mockito.eq(7L), Mockito.notNull());
        Mockito.verify(teamJoinLinkService).disable(Mockito.eq(7L), Mockito.notNull());
    }

    @Test
    public void aBusinessConstraintOnJoiningComesBackAsA4xx() throws Exception {
        Mockito.doThrow(new ru.sfedu.teamselection.exception.ConstraintViolationException("Студент уже состоит в команде"))
                .when(teamJoinLinkService).join(Mockito.eq("tok"), Mockito.notNull());

        mockMvc.perform(post("/api/v1/teams/join/tok")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isBadRequest());
    }
}
