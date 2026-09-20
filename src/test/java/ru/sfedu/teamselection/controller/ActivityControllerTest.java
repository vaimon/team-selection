package ru.sfedu.teamselection.controller;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.sfedu.teamselection.advice.ApiExceptionHandler;
import ru.sfedu.teamselection.config.SecurityConfig;
import ru.sfedu.teamselection.config.security.SimpleAuthenticationSuccessHandler;
import ru.sfedu.teamselection.domain.Role;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.dto.activity.ActivityEntryDto;
import ru.sfedu.teamselection.enums.ActivityAction;
import ru.sfedu.teamselection.mapper.PageResponseMapper;
import ru.sfedu.teamselection.service.ActivityService;
import ru.sfedu.teamselection.service.security.AzureOidcUserService;
import ru.sfedu.teamselection.service.security.CurrentAuthoritiesResolver;
import ru.sfedu.teamselection.service.security.Oauth2UserService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * История на уровне HTTP: только администратор, фильтры доходят до сервиса как есть.
 */
@ActiveProfiles("test")
@Import({SecurityConfig.class, ApiExceptionHandler.class, PageResponseMapper.class})
@WebMvcTest(ActivityController.class)
class ActivityControllerTest {

    @MockitoBean
    private ActivityService activityService;
    @MockitoBean
    private SimpleAuthenticationSuccessHandler simpleAuthenticationSuccessHandler;
    @MockitoBean
    private Oauth2UserService oauth2UserService;
    @MockitoBean
    private AzureOidcUserService azureOidcUserService;
    @MockitoBean
    private CurrentAuthoritiesResolver currentAuthoritiesResolver;

    @Autowired
    private MockMvc mockMvc;

    private final User admin = User.builder()
            .id(1L)
            .fio("admin")
            .email("admin@sfedu.ru")
            .isEnabled(true)
            .role(Role.builder().id(3L).name("ROLE_ADMIN").build())
            .build();

    private final User student = User.builder()
            .id(2L)
            .fio("A B C")
            .email("student@sfedu.ru")
            .isEnabled(true)
            .role(Role.builder().id(4L).name("ROLE_STUDENT").build())
            .build();

    private static Page<ActivityEntryDto> oneEntry() {
        return new PageImpl<>(List.of(new ActivityEntryDto(
                7L,
                LocalDateTime.of(2026, 11, 2, 12, 30),
                ActivityAction.MEMBER_MOVED,
                "Иванов перенесён из «Альфа» в «Бета»",
                1L, "admin", "admin@sfedu.ru", 2L, 3L, 4L)));
    }

    @Test
    void anAdminReadsTheHistory() throws Exception {
        Mockito.doReturn(oneEntry()).when(activityService)
                .history(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        mockMvc.perform(get(ActivityController.HISTORY)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].summary").value("Иванов перенесён из «Альфа» в «Бета»"))
                .andExpect(jsonPath("$.content[0].action").value("MEMBER_MOVED"))
                .andExpect(jsonPath("$.content[0].at").value("2026-11-02T12:30:00"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void theFiltersReachTheServiceUntouched() throws Exception {
        Mockito.doReturn(oneEntry()).when(activityService)
                .history(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        mockMvc.perform(get(ActivityController.HISTORY)
                        .param("teamId", "2")
                        .param("studentId", "4")
                        .param("actorUserId", "1")
                        .param("from", "2026-10-01T00:00:00")
                        .param("to", "2026-10-31T23:59:59")
                        .param("page", "1")
                        .param("size", "10")
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        Mockito.verify(activityService).history(
                Mockito.eq(2L), Mockito.eq(4L), Mockito.eq(1L),
                Mockito.eq(LocalDateTime.of(2026, 10, 1, 0, 0)),
                Mockito.eq(LocalDateTime.of(2026, 10, 31, 23, 59, 59)),
                pageable.capture());
        org.junit.jupiter.api.Assertions.assertEquals(PageRequest.of(1, 10), pageable.getValue());
    }

    @Test
    void aStudentSeesNeitherTheHistoryNorThePurge() throws Exception {
        mockMvc.perform(get(ActivityController.HISTORY)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ActivityController.PURGE)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(student)))
                .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(activityService);
    }

    @Test
    void anAdminPurgesAndSeesHowManyWentAway() throws Exception {
        Mockito.doReturn(12L).when(activityService).purge();

        mockMvc.perform(post(ActivityController.PURGE)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(12));
    }
}
