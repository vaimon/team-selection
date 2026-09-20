package ru.sfedu.teamselection.controller;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fail-closed реестр окна набора (#6).
 *
 * <p>Каждая мутирующая ручка обязана явно объявить, запирается ли она окном. Новая ручка, которой нет
 * ни в одном списке, роняет этот тест — незакрытый эндпоинт должен быть красным тестом, а не тихой
 * дырой. Это важно потому, что #9 (выход, исключение, передача капитанства, роспуск) и #13 (join-link)
 * добавят ровно те мутации, которые issue требует запирать, но приедут уже после этой задачи.
 */
class SelectionWindowCoverageTest {
    /** Ручки, проходящие через SelectionWindowService.assertStudentMutationAllowed. */
    private static final Set<String> WINDOW_GUARDED = Set.of(
            "ApplicationController#createApplication",
            "ApplicationController#update",
            "TeamController#createTeam",
            "TeamController#updateTeam",
            "TeamController#removeMember",
            "TeamController#leaveTeam",
            "TeamController#transferCaptaincy",
            "TeamController#disbandTeam",
            "TeamController#issueJoinLink",
            "TeamController#joinByLink"
    );

    /** Ручки вне окна — с причиной, почему это осознанно. */
    private static final Map<String, String> EXEMPT = Map.ofEntries(
            Map.entry("ApplicationController#delete", "DELETE: только администратор (SecurityConfig)"),
            Map.entry("StudentController#createStudent", "регистрация и анкета доступны до открытия набора"),
            Map.entry("StudentController#updateStudent", "правка своего профиля окном не запирается"),
            Map.entry("StudentController#deleteStudent", "DELETE: только администратор (SecurityConfig)"),
            Map.entry("TeamController#deleteTeam", "DELETE: только администратор (SecurityConfig)"),
            Map.entry("TeamController#addStudentToTeam", "только администратор: он и разбирает составы после закрытия"),
            Map.entry("TeamController#disableJoinLink",
                    "отозвать утёкшую ссылку нужно и после закрытия набора; отключение ничего не открывает"),
            Map.entry("UserController#putUser", "правка своего профиля окном не запирается"),
            Map.entry("UserController#assignRole", "только администратор"),
            Map.entry("UserController#deleteUser", "DELETE: только администратор (SecurityConfig)"),
            Map.entry("CompositionBoardController#move", "только администратор: разбирает составы после закрытия"),
            Map.entry("CompositionBoardController#setTargets", "только администратор: разбирает составы после закрытия"),
            Map.entry("CompositionBoardController#changeLead", "только администратор: разбирает составы после закрытия"),
            Map.entry("CompositionBoardController#dissolve", "только администратор: разбирает составы после закрытия"),
            Map.entry("TrackController#createTrack", "только администратор: настройка набора"),
            Map.entry("TrackController#updateTrack", "только администратор: настройка набора"),
            Map.entry("TrackController#deleteTrack", "только администратор: настройка набора"),
            Map.entry("TrackController#startNewSelection", "только администратор: сам открывает следующее окно"),
            Map.entry("TrackHandOverController#handOver", "только администратор: передача идёт после закрытия"),
            Map.entry("TrackHandOverController#cancelHandOver", "только администратор: передача идёт после закрытия"),
            Map.entry("IntegrationController#handOver", "вызывает core по API-ключу после закрытия набора"),
            Map.entry("ActivityController#purge", "только администратор: чистка истории"),
            Map.entry("ProjectTypeController#createProjectType", "только администратор: словарь"),
            Map.entry("ProjectTypeController#deleteProjectType", "только администратор: словарь"),
            Map.entry("TechnologyController#createTechnology", "только администратор: словарь"),
            Map.entry("TechnologyController#deleteTechnology", "только администратор: словарь")
    );

    @Test
    void everyMutatingEndpointDeclaresAWindowPolicy() {
        Set<String> declared = new TreeSet<>(WINDOW_GUARDED);
        declared.addAll(EXEMPT.keySet());

        assertThat(MutatingEndpoints.discover())
                .as("новая мутирующая ручка должна попасть либо в WINDOW_GUARDED, либо в EXEMPT с причиной")
                .isEqualTo(declared);
    }
}
