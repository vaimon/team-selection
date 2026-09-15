package ru.sfedu.teamselection.controller;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.sfedu.teamselection.config.SecurityConfig;
import ru.sfedu.teamselection.config.security.SimpleAuthenticationSuccessHandler;
import ru.sfedu.teamselection.domain.Role;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.dto.track.TrackDto;
import ru.sfedu.teamselection.enums.TrackType;
import ru.sfedu.teamselection.exception.CustomExceptionHandler;
import ru.sfedu.teamselection.exception.NotFoundException;
import ru.sfedu.teamselection.mapper.track.TrackDtoMapper;
import ru.sfedu.teamselection.service.TrackService;
import ru.sfedu.teamselection.service.audit.AuditService;
import ru.sfedu.teamselection.service.security.AzureOidcUserService;
import ru.sfedu.teamselection.service.security.CurrentAuthoritiesResolver;
import ru.sfedu.teamselection.service.security.Oauth2UserService;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test class for the {@link TrackController}
 */
@ActiveProfiles("test")
@Import(SecurityConfig.class)
@WebMvcTest({TrackController.class, CustomExceptionHandler.class})
public class TrackControllerTest {
    @MockitoBean
    private SimpleAuthenticationSuccessHandler simpleAuthenticationSuccessHandler;
    @MockitoBean
    private Oauth2UserService oauth2UserService;
    @MockitoBean
    private AzureOidcUserService azureOidcUserService;
    @MockitoBean
    private CurrentAuthoritiesResolver currentAuthoritiesResolver;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private TrackService trackService;
    @MockitoBean
    private TrackDtoMapper TrackDtoMapper;

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

    private final List<Track> trackList = List.of(
            Track.builder()
                    .id(1L)
                    .name("1")
                    .about("some text about")
                    .startDate(LocalDate.of(2025, 9, 12))
                    .endDate(LocalDate.of(2026, 9, 11))
                    .type(TrackType.bachelor)
                    .firstYearTarget(2)
                    .secondYearTarget(4)
                    .build(),
            Track.builder()
                    .id(2L)
                    .name("2")
                    .about("some text about 2")
                    .startDate(LocalDate.of(2025, 9, 12))
                    .endDate(LocalDate.of(2026, 9, 11))
                    .type(TrackType.master)
                    .firstYearTarget(2)
                    .secondYearTarget(4)
                    .build()
    );

    @Test
    public void findAll() throws Exception {
        Mockito.doReturn(List.of(
                TrackDto.builder()
                        .id(2L)
                        .name("2")
                        .about("some text about 2")
                        .startDate(LocalDate.of(2025, 9, 12))
                        .endDate(LocalDate.of(2026, 9, 11))
                        .type("master")
                        .firstYearTarget(2)
                        .secondYearTarget(4)
                        .build()
                )
        ).when(trackService).findAll();

        mockMvc.perform(get("/api/v1/tracks")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isOk());
    }

    @Test
    public void whenFindByExistingIdThenReturn200() throws Exception {
        Mockito.doReturn(trackList.get(0)).when(trackService).findByIdOrElseThrow(2L);

        mockMvc.perform(get("/api/v1/tracks/{id}".replace("{id}", "2"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isOk());
    }

    @Test
    public void whenFindByNotExistingIdThenReturn404() throws Exception {
        Mockito.doThrow(new NotFoundException("text")).when(trackService).findByIdOrElseThrow(anyLong());

        mockMvc.perform(get("/api/v1/tracks/{id}".replace("{id}", "6000"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    public void whenCreateTrackFromAdminThenReturn200() throws Exception {
        String track = """
                {
                   "name": "Spring Boot Fundamentals",
                   "about": "A comprehensive track covering Spring Boot basics",
                   "startDate": "2024-01-15",
                   "endDate": "2024-03-15",
                   "type": "BOOTCAMP",
                   "firstYearTarget": 3,
                   "secondYearTarget": 3
                 }""";

        mockMvc.perform(post("/api/v1/tracks")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin))
                        .content(track)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void whenCreateTrackNotFromAdminThenReturn403() throws Exception {
        String track = """
                {
                   "name": "Spring Boot Fundamentals",
                   "about": "A comprehensive track covering Spring Boot basics",
                   "startDate": "2024-01-15",
                   "endDate": "2024-03-15",
                   "type": "BOOTCAMP",
                   "firstYearTarget": 3,
                   "secondYearTarget": 3
                 }""";

        mockMvc.perform(post("/api/v1/tracks")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser))
                        .content(track)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    public void whenUpdateTrackFromAdminThenReturn200() throws Exception {
        Mockito.doReturn(trackList.get(0)).when(trackService).update(Mockito.eq(1L), Mockito.any());
        String track = """
                {
                   "id": 1,
                   "name": "Spring Boot Fundamentals",
                   "about": "A comprehensive track covering Spring Boot basics",
                   "startDate": "2024-01-15",
                   "endDate": "2024-03-15",
                   "type": "BOOTCAMP",
                   "firstYearTarget": 3,
                   "secondYearTarget": 3
                 }""";

        mockMvc.perform(put("/api/v1/tracks/{id}".replace("{id}", "1"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin))
                        .content(track)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void whenUpdateTrackNotFromAdminThenReturn403() throws Exception {
        Mockito.doReturn(trackList.get(0)).when(trackService).update(Mockito.eq(1L), Mockito.any());
        String track = """
                {
                   "id": 1,
                   "name": "Spring Boot Fundamentals",
                   "about": "A comprehensive track covering Spring Boot basics",
                   "startDate": "2024-01-15",
                   "endDate": "2024-03-15",
                   "type": "BOOTCAMP",
                   "firstYearTarget": 3,
                   "secondYearTarget": 3
                 }""";

        mockMvc.perform(put("/api/v1/tracks/{id}".replace("{id}", "1"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser))
                        .content(track)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    public void deleteTrackFromAdmin() throws Exception {
        Mockito.doNothing().when(trackService).deleteById(Mockito.any());

        mockMvc.perform(delete("/api/v1/tracks/{trackId}".replace("{trackId}", "1"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    public void deleteTrackFromGenericUserShouldFail() throws Exception {
        Mockito.doNothing().when(trackService).deleteById(Mockito.any());

        mockMvc.perform(delete("/api/v1/tracks/{id}".replace("{id}", "2"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    public void whenFindCurrentTrackThenReturnActiveTrackWithoutTeams() throws Exception {
        Track active = Track.builder().id(7L).name("Отбор 2026").active(true).build();
        Mockito.doReturn(active).when(trackService).getActive();
        Mockito.doReturn(TrackDto.builder().id(7L).name("Отбор 2026").active(true).build())
                .when(TrackDtoMapper).mapToDtoWithoutTeams(active);

        mockMvc.perform(get("/api/v1/tracks/current")
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    public void whenNoSelectionStartedThenCurrentTrackReturns404() throws Exception {
        Mockito.doThrow(new NotFoundException("Отбор не настроен")).when(trackService).getActive();

        mockMvc.perform(get("/api/v1/tracks/current")
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    public void whenStartNewSelectionFromAdminThenReturn200() throws Exception {
        mockMvc.perform(post("/api/v1/tracks/new-selection")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin))
                        .content("{\"name\": \"Отбор 2027\", \"startDate\": \"2027-10-01\"}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Mockito.verify(trackService).startNewSelection(Mockito.any());
    }

    @Test
    public void whenStartNewSelectionNotFromAdminThenReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/tracks/new-selection")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(genericStudentUser))
                        .content("{}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        Mockito.verify(trackService, Mockito.never()).startNewSelection(Mockito.any());
    }

    // access matrix, vaimon/team-selection#7

    @Test
    public void studentWithoutQuestionnaireSeesTheCurrentSelectionButNotTrackRosters() throws Exception {
        Track active = Track.builder().id(7L).name("Отбор 2026").active(true).build();
        Mockito.doReturn(active).when(trackService).getActive();

        mockMvc.perform(get("/api/v1/tracks/current")
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(studentWithoutQuestionnaire)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tracks")
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(studentWithoutQuestionnaire)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tracks/7")
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(studentWithoutQuestionnaire)))
                .andExpect(status().isForbidden());
    }
}
