package ru.sfedu.teamselection.controller;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.sfedu.teamselection.advice.ApiExceptionHandler;
import ru.sfedu.teamselection.config.SecurityConfig;
import ru.sfedu.teamselection.config.security.SimpleAuthenticationSuccessHandler;
import ru.sfedu.teamselection.domain.Role;
import ru.sfedu.teamselection.domain.Team;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.dto.board.BoardMoveRequest;
import ru.sfedu.teamselection.dto.board.CompositionBoardDto;
import ru.sfedu.teamselection.enums.ConflictReason;
import ru.sfedu.teamselection.exception.ConflictException;
import ru.sfedu.teamselection.service.CompositionBoardService;
import ru.sfedu.teamselection.service.UserService;
import ru.sfedu.teamselection.service.security.AzureOidcUserService;
import ru.sfedu.teamselection.service.security.CurrentAuthoritiesResolver;
import ru.sfedu.teamselection.service.security.Oauth2UserService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Доска состава на уровне HTTP: доступ только администратору, отказы по состоянию — 409 с кодом,
 * негодный ввод — 400 до сервиса.
 */
@ActiveProfiles("test")
@Import({SecurityConfig.class, ApiExceptionHandler.class})
@WebMvcTest(CompositionBoardController.class)
class CompositionBoardControllerTest {

    @MockitoBean
    private CompositionBoardService boardService;
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

    private static final String MOVE = """
            {"studentId": 5, "toTeamId": 1, "toVersion": 0, "allowOverTarget": false}
            """;

    @BeforeEach
    void currentUserIsTheAdmin() {
        Mockito.doReturn(admin).when(userService).getCurrentUser();
    }

    private static CompositionBoardDto emptyBoard() {
        return new CompositionBoardDto(1L, "Набор 2026", 3, 3, List.of(), List.of());
    }

    @Test
    void anAdminSeesTheBoard() throws Exception {
        Mockito.doReturn(emptyBoard()).when(boardService).board();

        mockMvc.perform(get(CompositionBoardController.BOARD)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackName").value("Набор 2026"))
                .andExpect(jsonPath("$.firstYearTarget").value(3));
    }

    @Test
    void aStudentIsNotLetNearTheBoard() throws Exception {
        mockMvc.perform(get(CompositionBoardController.BOARD)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(CompositionBoardController.MOVES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MOVE)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(student)))
                .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(boardService);
    }

    @Test
    void aMoveIsPassedThroughAsAsked() throws Exception {
        Mockito.doReturn(emptyBoard()).when(boardService).move(Mockito.any(), Mockito.any());

        mockMvc.perform(post(CompositionBoardController.MOVES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MOVE)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isOk());

        Mockito.verify(boardService).move(
                new BoardMoveRequest(5L, null, null, 1L, 0L, false, null), admin);
    }

    @Test
    void aRefusalIsAConflictThatNamesItsReason() throws Exception {
        Mockito.doThrow(new ConflictException(ConflictReason.OVER_TARGET, "Команда уже набрала цель"))
                .when(boardService).move(Mockito.any(), Mockito.any());

        mockMvc.perform(post(CompositionBoardController.MOVES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MOVE)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OVER_TARGET"))
                .andExpect(jsonPath("$.message").value("Команда уже набрала цель"));
    }

    /** Гонку между чтением и записью ловит сам Hibernate — для клиента это та же устаревшая доска. */
    @Test
    void aLostRaceIsReportedAsAStaleBoard() throws Exception {
        Mockito.doThrow(new ObjectOptimisticLockingFailureException(Team.class, 1L))
                .when(boardService).move(Mockito.any(), Mockito.any());

        mockMvc.perform(post(CompositionBoardController.MOVES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MOVE)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void otherErrorsDoNotCarryACode() throws Exception {
        mockMvc.perform(post(CompositionBoardController.MOVES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toTeamId\": 1, \"toVersion\": 0}")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").doesNotExist());

        Mockito.verifyNoInteractions(boardService);
    }

    @Test
    void aNegativeTargetIsRejectedBeforeTheService() throws Exception {
        mockMvc.perform(put(CompositionBoardController.TARGETS, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 0, \"firstYearTarget\": -1}")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(boardService);
    }

    @Test
    void dissolvingWithoutAVersionIsRejectedBeforeTheService() throws Exception {
        mockMvc.perform(post(CompositionBoardController.DISSOLVE, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(admin)))
                .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(boardService);
    }
}
