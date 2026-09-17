package ru.sfedu.teamselection.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.sfedu.teamselection.advice.ApiExceptionHandler;
import ru.sfedu.teamselection.config.SecurityConfig;
import ru.sfedu.teamselection.config.security.SimpleAuthenticationSuccessHandler;
import ru.sfedu.teamselection.domain.Role;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.dto.AdminOverviewDto;
import ru.sfedu.teamselection.service.AdminOverviewService;
import ru.sfedu.teamselection.service.audit.AuditService;
import ru.sfedu.teamselection.service.security.AzureOidcUserService;
import ru.sfedu.teamselection.service.security.CurrentAuthoritiesResolver;
import ru.sfedu.teamselection.service.security.Oauth2UserService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Обзор набора на уровне HTTP: кто допущен и что происходит с негодным параметром.
 */
@ActiveProfiles("test")
@Import({SecurityConfig.class, ApiExceptionHandler.class})
@WebMvcTest(AdminOverviewController.class)
class AdminOverviewControllerTest {

    @MockitoBean
    private AdminOverviewService adminOverviewService;
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

    private static AdminOverviewDto anyOverview() {
        return new AdminOverviewDto(
                1L,
                "Набор 2026",
                new AdminOverviewDto.Students(10, 6, 4, 3, 7, 4, 3),
                new AdminOverviewDto.Teams(2, 1, 1),
                new AdminOverviewDto.Applications(5, 2, 1, 0, 3)
        );
    }

    @Test
    void anAdminSeesTheOverview() throws Exception {
        Mockito.doReturn(anyOverview()).when(adminOverviewService).overview(3);

        mockMvc.perform(get(AdminOverviewController.OVERVIEW)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackName").value("Набор 2026"))
                .andExpect(jsonPath("$.students.total").value(10))
                .andExpect(jsonPath("$.teams.incomplete").value(1))
                .andExpect(jsonPath("$.applications.staleThresholdDays").value(3));
    }

    @Test
    void aStudentIsNotLetNearTheOverview() throws Exception {
        mockMvc.perform(get(AdminOverviewController.OVERVIEW)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(student)))
                .andExpect(status().isForbidden());

        Mockito.verify(adminOverviewService, Mockito.never()).overview(Mockito.anyInt());
    }

    @Test
    void theThresholdIsPassedThroughAsAsked() throws Exception {
        Mockito.doReturn(anyOverview()).when(adminOverviewService).overview(7);

        mockMvc.perform(get(AdminOverviewController.OVERVIEW)
                        .param("pendingOlderThanDays", "7")
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isOk());

        Mockito.verify(adminOverviewService).overview(7);
    }

    /**
     * Отрицательный порог — ошибка клиента, а не сервера. До этой задачи такой запрос падал в
     * catch-all и отвечал 500: в проекте не было ни одной валидации query-параметра, поэтому
     * соответствующее исключение никто не обрабатывал.
     */
    @Test
    void aNegativeThresholdIsARejectedRequestNotAServerError() throws Exception {
        mockMvc.perform(get(AdminOverviewController.OVERVIEW)
                        .param("pendingOlderThanDays", "-1")
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isBadRequest());

        Mockito.verify(adminOverviewService, Mockito.never()).overview(Mockito.anyInt());
    }
}
