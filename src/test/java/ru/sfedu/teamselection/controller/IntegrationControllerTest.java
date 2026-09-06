package ru.sfedu.teamselection.controller;

import java.time.LocalDate;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.sfedu.teamselection.config.IntegrationSecurityConfig;
import ru.sfedu.teamselection.config.SecurityConfig;
import ru.sfedu.teamselection.config.security.ApiKeyAuthFilter;
import ru.sfedu.teamselection.config.security.SimpleAuthenticationSuccessHandler;
import ru.sfedu.teamselection.dto.integration.IntegrationRosterDto;
import ru.sfedu.teamselection.dto.integration.IntegrationStudentDto;
import ru.sfedu.teamselection.dto.integration.IntegrationTeamDto;
import ru.sfedu.teamselection.dto.integration.IntegrationTrackDto;
import ru.sfedu.teamselection.service.audit.AuditService;
import ru.sfedu.teamselection.service.integration.IntegrationRosterService;
import ru.sfedu.teamselection.service.security.AzureOidcUserService;
import ru.sfedu.teamselection.service.security.Oauth2UserService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Тест интеграционного API. Импортирует обе security-цепочки, потому что половина
 * проверяемого здесь — то, что новая цепочка не задевает существующие ручки.
 */
@ActiveProfiles("test")
@Import({SecurityConfig.class, IntegrationSecurityConfig.class})
@WebMvcTest(IntegrationController.class)
@TestPropertySource(properties = "integration.api-key=" + IntegrationControllerTest.VALID_KEY)
public class IntegrationControllerTest {
    static final String VALID_KEY = "integration-test-key";

    private static final long TRACK_ID = 1L;
    private static final String ROSTER_URL = "/api/integration/v1/tracks/1/roster";
    private static final String TRACKS_URL = "/api/integration/v1/tracks";

    @MockitoBean
    private IntegrationRosterService integrationRosterService;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private Oauth2UserService oauth2UserService;
    @MockitoBean
    private AzureOidcUserService azureOidcUserService;
    @MockitoBean
    private SimpleAuthenticationSuccessHandler simpleAuthenticationSuccessHandler;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    public void stubRoster() {
        Mockito.when(integrationRosterService.findRoster(TRACK_ID)).thenReturn(roster());
    }

    @Test
    public void servesTheRosterWhenTheKeyMatches() throws Exception {
        mockMvc.perform(get(ROSTER_URL).header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.track.id").value(1))
                .andExpect(jsonPath("$.track.type").value("bachelor"))
                .andExpect(jsonPath("$.track.startDate").value("2026-09-01"))
                .andExpect(jsonPath("$.track.endDate").value("2027-06-30"))
                .andExpect(jsonPath("$.teams[0].name").value("Alpha"))
                .andExpect(jsonPath("$.teams[0].projectType").value("Web"))
                .andExpect(jsonPath("$.teams[0].captainStudentId").value(10))
                .andExpect(jsonPath("$.teams[0].isFull").value(true))
                .andExpect(jsonPath("$.students[0].fio").value("Иванов Иван"))
                .andExpect(jsonPath("$.students[0].email").value("ivanov@sfedu.ru"))
                .andExpect(jsonPath("$.students[0].isCaptain").value(true))
                .andExpect(jsonPath("$.students[0].teamId").value(100));
    }

    /**
     * Команда без типа проекта и студент без команды — штатные состояния, а не
     * повод обвалить выгрузку.
     */
    @Test
    public void keepsMissingProjectTypeAndTeamAsNulls() throws Exception {
        mockMvc.perform(get(ROSTER_URL).header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams[1].projectType").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.teams[1].captainStudentId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.students[1].teamId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.students[1].hasTeam").value(false));
    }

    @Test
    public void rejectsTheRosterWithoutAKey() throws Exception {
        mockMvc.perform(get(ROSTER_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void rejectsTheRosterWithAWrongKey() throws Exception {
        mockMvc.perform(get(ROSTER_URL).header(ApiKeyAuthFilter.API_KEY_HEADER, "nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void rejectsTheTrackListWithoutAKey() throws Exception {
        mockMvc.perform(get(TRACKS_URL))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Ключ открывает только интеграционный префикс: обычные ручки как требовали
     * сессию, так и требуют.
     */
    @Test
    public void doesNotUnlockTheRegularApi() throws Exception {
        mockMvc.perform(get("/api/v1/students").header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isUnauthorized());
    }

    private IntegrationRosterDto roster() {
        return IntegrationRosterDto.builder()
                .track(IntegrationTrackDto.builder()
                        .id(TRACK_ID)
                        .name("2026/2027")
                        .type("bachelor")
                        .startDate(LocalDate.of(2026, 9, 1))
                        .endDate(LocalDate.of(2027, 6, 30))
                        .build())
                .teams(List.of(
                        IntegrationTeamDto.builder()
                                .id(100L)
                                .name("Alpha")
                                .projectDescription("Что-то полезное")
                                .projectType("Web")
                                .captainStudentId(10L)
                                .isFull(true)
                                .quantityOfStudents(4)
                                .build(),
                        IntegrationTeamDto.builder()
                                .id(101L)
                                .name("Beta")
                                .projectDescription(null)
                                .projectType(null)
                                .captainStudentId(null)
                                .isFull(false)
                                .quantityOfStudents(2)
                                .build()))
                .students(List.of(
                        IntegrationStudentDto.builder()
                                .id(10L)
                                .fio("Иванов Иван")
                                .email("ivanov@sfedu.ru")
                                .course(3)
                                .groupNumber(1)
                                .hasTeam(true)
                                .isCaptain(true)
                                .teamId(100L)
                                .build(),
                        IntegrationStudentDto.builder()
                                .id(11L)
                                .fio("Петров Пётр")
                                .email("petrov@sfedu.ru")
                                .course(2)
                                .groupNumber(2)
                                .hasTeam(false)
                                .isCaptain(false)
                                .teamId(null)
                                .build()))
                .build();
    }
}
