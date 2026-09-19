package ru.sfedu.teamselection.controller;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fail-closed реестр передачи в кабинет ПД (#15).
 *
 * <p>После передачи набор только для чтения для всех. Каждая мутирующая ручка обязана объявить,
 * запирается ли она передачей; новая ручка без объявления роняет тест. Поведение запертых ручек
 * проверяет HandOverLockTest — по представителю на каждый путь, которым приходит запрет.
 */
class HandOverCoverageTest {

    /**
     * Запрет приходит одним из трёх путей: проверка окна (активный набор), assertWritable (набор
     * команды) или явный assertNotHandedOver (анкета, студент, пользователь с анкетой, удаление заявки,
     * отключение ссылки).
     */
    private static final Set<String> LOCKED = Set.of(
            "ApplicationController#createApplication",
            "ApplicationController#update",
            "ApplicationController#delete",
            "StudentController#createStudent",
            "StudentController#updateStudent",
            "StudentController#deleteStudent",
            "TeamController#createTeam",
            "TeamController#updateTeam",
            "TeamController#deleteTeam",
            "TeamController#addStudentToTeam",
            "TeamController#removeMember",
            "TeamController#leaveTeam",
            "TeamController#transferCaptaincy",
            "TeamController#disbandTeam",
            "TeamController#issueJoinLink",
            "TeamController#disableJoinLink",
            "TeamController#joinByLink",
            "CompositionBoardController#move",
            "CompositionBoardController#setTargets",
            "CompositionBoardController#changeLead",
            "CompositionBoardController#dissolve",
            "TrackController#updateTrack",
            "UserController#putUser"
    );

    private static final Map<String, String> EXEMPT = Map.ofEntries(
            Map.entry("TrackController#startNewSelection", "следующий набор стартует и после передачи — так требует issue"),
            Map.entry("TrackController#createTrack", "новый трек ещё не передан"),
            Map.entry("TrackController#deleteTrack", "набор с участниками удалить нельзя и без передачи"),
            Map.entry("TrackHandOverController#handOver", "сама передача"),
            Map.entry("TrackHandOverController#cancelHandOver", "отмена ошибочной передачи"),
            Map.entry("IntegrationController#handOver", "сама передача, её вызывает core"),
            Map.entry("UserController#assignRole", "аккаунт пользователя, а не состав набора"),
            Map.entry("UserController#deleteUser", "деактивация аккаунта, а не состав набора"),
            Map.entry("ProjectTypeController#createProjectType", "словарь, не относится к набору"),
            Map.entry("ProjectTypeController#deleteProjectType", "словарь, не относится к набору"),
            Map.entry("TechnologyController#createTechnology", "словарь, не относится к набору"),
            Map.entry("TechnologyController#deleteTechnology", "словарь, не относится к набору")
    );

    @Test
    void everyMutatingEndpointDeclaresAHandOverPolicy() {
        Set<String> declared = new TreeSet<>(LOCKED);
        declared.addAll(EXEMPT.keySet());

        assertThat(MutatingEndpoints.discover())
                .as("новая мутирующая ручка должна попасть либо в LOCKED, либо в EXEMPT с причиной")
                .isEqualTo(declared);
    }
}
