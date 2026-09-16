package ru.sfedu.teamselection.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
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
 * CSRF protection (vaimon/team-selection#11) as the SPA meets it: the token comes from the cookie
 * the backend issues, not from the test harness.
 *
 * <p>The class deliberately runs in its own application context. {@code csrf()} from
 * SecurityMockMvcRequestPostProcessors swaps the token repository for the whole servlet context,
 * so in a context shared with the other controller tests the real CookieCsrfTokenRepository would
 * never be reached and these assertions would test nothing.
 */
@ActiveProfiles("test")
@Import(SecurityConfig.class)
@WebMvcTest(ApplicationController.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfProtectionTest {
    private static final String TOKEN_COOKIE = "XSRF-TOKEN";
    private static final String TOKEN_HEADER = "X-XSRF-TOKEN";
    private static final String REQUEST_BODY =
            "{\"team_id\": 1, \"student_id\": 5, \"status\": \"sent\", \"type\": \"request\"}";

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

    private Cookie issuedToken() throws Exception {
        Mockito.doReturn(new PageImpl<>(java.util.List.of(), PageRequest.of(0, 20), 0))
                .when(applicationService).findAll(Mockito.any(), Mockito.any(), Mockito.any());

        return mockMvc.perform(get(ApplicationController.FIND_ALL)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .oauth2User(withRole("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.cookie().exists(TOKEN_COOKIE))
                .andExpect(MockMvcResultMatchers.cookie().httpOnly(TOKEN_COOKIE, false))
                .andReturn().getResponse().getCookie(TOKEN_COOKIE);
    }

    @Test
    void safeRequestIssuesTheTokenCookieForTheSpa() throws Exception {
        issuedToken();
    }

    @Test
    void mutationWithoutTheTokenIsRejected() throws Exception {
        mockMvc.perform(post(ApplicationController.CREATE_APPLICATION)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .oauth2User(withRole("ROLE_PARTICIPANT")))
                        .content(REQUEST_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(applicationService);
    }

    @Test
    void mutationWithTheTokenFromTheCookieSucceeds() throws Exception {
        Cookie token = issuedToken();

        mockMvc.perform(post(ApplicationController.CREATE_APPLICATION)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .oauth2User(withRole("ROLE_PARTICIPANT")))
                        .cookie(token)
                        .header(TOKEN_HEADER, token.getValue())
                        .content(REQUEST_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Mockito.verify(applicationService).create(Mockito.any(), Mockito.any());
    }
}
