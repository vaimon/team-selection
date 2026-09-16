package ru.sfedu.teamselection.advice;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import ru.sfedu.teamselection.config.SecurityConfig;
import ru.sfedu.teamselection.config.security.SimpleAuthenticationSuccessHandler;
import ru.sfedu.teamselection.controller.ApplicationController;
import ru.sfedu.teamselection.domain.Role;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.exception.ForbiddenException;
import ru.sfedu.teamselection.exception.NotFoundException;
import ru.sfedu.teamselection.mapper.application.ApplicationDtoMapper;
import ru.sfedu.teamselection.mapper.application.ApplicationMapper;
import ru.sfedu.teamselection.service.ApplicationService;
import ru.sfedu.teamselection.service.UserService;
import ru.sfedu.teamselection.service.audit.AuditService;
import ru.sfedu.teamselection.service.security.AzureOidcUserService;
import ru.sfedu.teamselection.service.security.CurrentAuthoritiesResolver;
import ru.sfedu.teamselection.service.security.Oauth2UserService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Status codes the API answers with (vaimon/team-selection#11): an unknown route and a missing
 * entity used to come back as 500, which told the frontend nothing it could act on.
 */
@ActiveProfiles("test")
@Import(SecurityConfig.class)
@WebMvcTest(ApplicationController.class)
class ApiExceptionHandlerTest {
    @MockitoBean
    private ApplicationService applicationService;
    @MockitoBean
    private ApplicationMapper applicationMapper;
    @MockitoBean
    private ApplicationDtoMapper applicationDtoMapper;
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
    private AuditService auditService;

    @Autowired
    private MockMvc mockMvc;

    private static SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(
                User.builder().id(9L).fio("X").email("x@sfedu.ru").isEnabled(true)
                        .role(Role.builder().id(1L).name("ROLE_ADMIN").build())
                        .build());
    }

    @Test
    void unknownRouteIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/there-is-no-such-thing").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingEntityIsNotFound() throws Exception {
        Mockito.doThrow(new NotFoundException("Заявка с id `404` не найдена"))
                .when(applicationService).findAll(Mockito.any(), Mockito.any(), Mockito.any());

        mockMvc.perform(get(ApplicationController.FIND_ALL).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("Заявка с id `404` не найдена"));
    }

    @Test
    void forbiddenOperationIsForbidden() throws Exception {
        Mockito.doThrow(new ForbiddenException("Нельзя"))
                .when(applicationService).findAll(Mockito.any(), Mockito.any(), Mockito.any());

        mockMvc.perform(get(ApplicationController.FIND_ALL).with(admin()))
                .andExpect(status().isForbidden());
    }

    /**
     * An unexpected failure stays a 500, but its message is for the log, not for the client.
     */
    @Test
    void unexpectedFailureDoesNotLeakItsMessage() throws Exception {
        Mockito.doThrow(new IllegalStateException("connection to team-selection-postgres refused"))
                .when(applicationService).findAll(Mockito.any(), Mockito.any(), Mockito.any());

        mockMvc.perform(get(ApplicationController.FIND_ALL).with(admin()))
                .andExpect(status().isInternalServerError())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value(Matchers.not(Matchers.containsString("postgres"))));
    }

    @Test
    void unknownRouteIsNotFoundForAMutationToo() throws Exception {
        mockMvc.perform(post("/api/v1/there-is-no-such-thing")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(admin()))
                .andExpect(status().isNotFound());
    }
}
