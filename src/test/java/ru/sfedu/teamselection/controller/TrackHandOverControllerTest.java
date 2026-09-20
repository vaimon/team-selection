package ru.sfedu.teamselection.controller;

import java.time.LocalDateTime;
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
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.dto.track.TrackDto;
import ru.sfedu.teamselection.mapper.track.TrackDtoMapper;
import ru.sfedu.teamselection.service.TrackService;
import ru.sfedu.teamselection.service.UserService;
import ru.sfedu.teamselection.service.security.AzureOidcUserService;
import ru.sfedu.teamselection.service.security.CurrentAuthoritiesResolver;
import ru.sfedu.teamselection.service.security.Oauth2UserService;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ручная передача в кабинет ПД и её отмена (#15): только администратор, время — ISO-строкой для баннера.
 */
@ActiveProfiles("test")
@Import({SecurityConfig.class, ApiExceptionHandler.class})
@WebMvcTest(TrackHandOverController.class)
class TrackHandOverControllerTest {

    @MockitoBean
    private TrackService trackService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private TrackDtoMapper trackDtoMapper;
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

    @Test
    void anAdminHandsOverAndTheBannerTimeIsAnIsoString() throws Exception {
        Track handedOver = Track.builder().id(7L).handedOverAt(LocalDateTime.of(2026, 11, 2, 12, 30)).build();
        Mockito.doReturn(handedOver).when(trackService).handOver(Mockito.eq(7L), Mockito.any());
        Mockito.doReturn(TrackDto.builder().id(7L).handedOverAt(handedOver.getHandedOverAt()).build())
                .when(trackDtoMapper).mapToDtoWithoutTeams(handedOver);

        mockMvc.perform(post(TrackHandOverController.HAND_OVER, 7)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handedOverAt").value("2026-11-02T12:30:00"));
    }

    @Test
    void anAdminCancelsAHandOver() throws Exception {
        Mockito.doReturn(Track.builder().id(7L).build()).when(trackService).cancelHandOver(Mockito.eq(7L), Mockito.any());

        mockMvc.perform(post(TrackHandOverController.CANCEL_HAND_OVER, 7)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isOk());

        Mockito.verify(trackService).cancelHandOver(Mockito.eq(7L), Mockito.any());
    }

    @Test
    void aStudentCanNeitherHandOverNorCancel() throws Exception {
        mockMvc.perform(post(TrackHandOverController.HAND_OVER, 7)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(TrackHandOverController.CANCEL_HAND_OVER, 7)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(student)))
                .andExpect(status().isForbidden());

        Mockito.verify(trackService, Mockito.never()).handOver(anyLong(), Mockito.any());
        Mockito.verify(trackService, Mockito.never()).cancelHandOver(anyLong(), Mockito.any());
    }
}
