package ru.sfedu.teamselection.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.sfedu.teamselection.config.SecurityConfig;
import ru.sfedu.teamselection.config.security.SimpleAuthenticationSuccessHandler;
import ru.sfedu.teamselection.domain.Role;
import ru.sfedu.teamselection.domain.User;
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
 * Access matrix for {@link ApplicationController} (vaimon/team-selection#7).
 */
@ActiveProfiles("test")
@Import(SecurityConfig.class)
@WebMvcTest({ApplicationController.class})
class ApplicationControllerTest {
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

    private static User withRole(String role) {
        return User.builder().id(9L).fio("X").email("x@sfedu.ru").isEnabled(true)
                .role(Role.builder().id(1L).name(role).build())
                .build();
    }

    @Test
    void applicationsAcrossAllTeamsAreAdminOnly() throws Exception {
        Mockito.doReturn(new org.springframework.data.domain.PageImpl<>(
                java.util.List.of(), org.springframework.data.domain.PageRequest.of(0, 20), 0)).when(applicationService).findAll(Mockito.any(), Mockito.any(), Mockito.any());

        mockMvc.perform(get(ApplicationController.FIND_ALL)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(withRole("ROLE_PARTICIPANT"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ApplicationController.FIND_ALL)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(withRole("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void studentWithoutQuestionnaireCannotApply() throws Exception {
        mockMvc.perform(post(ApplicationController.CREATE_APPLICATION)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(withRole("ROLE_STUDENT")))
                        .content("{\"team_id\": 1, \"student_id\": 5, \"type\": \"request\"}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(applicationService);
    }
}
