package ru.sfedu.teamselection.controller;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fail-closed реестр истории действий (#16).
 *
 * <p>Каждая мутирующая ручка объявляет, попадает ли она в историю. Новая ручка без объявления
 * роняет тест: иначе организатор однажды не найдёт в истории ровно то действие, из-за которого
 * и пришёл. Что записи действительно пишутся, проверяет ActivityRecordingTest.
 */
class ActivityCoverageTest {

    private static final Set<String> RECORDED = Set.of(
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
            "TrackController#startNewSelection",
            "TrackHandOverController#handOver",
            "TrackHandOverController#cancelHandOver",
            "IntegrationController#handOver",
            "UserController#putUser",
            "UserController#assignRole",
            "UserController#deleteUser"
    );

    private static final Map<String, String> NOT_RECORDED = Map.ofEntries(
            Map.entry("TrackController#createTrack", "заводит трек в обход набора; состава ещё нет"),
            Map.entry("TrackController#deleteTrack", "удалить можно только трек без участников"),
            Map.entry("ActivityController#purge", "сама чистка истории, её объём виден в ответе и в логе"),
            Map.entry("ProjectTypeController#createProjectType", "словарь, не относится к составу"),
            Map.entry("ProjectTypeController#deleteProjectType", "словарь, не относится к составу"),
            Map.entry("TechnologyController#createTechnology", "словарь, не относится к составу"),
            Map.entry("TechnologyController#deleteTechnology", "словарь, не относится к составу")
    );

    @Test
    void everyMutatingEndpointDeclaresWhetherItIsRecorded() {
        Set<String> declared = new TreeSet<>(RECORDED);
        declared.addAll(NOT_RECORDED.keySet());

        assertThat(MutatingEndpoints.discover())
                .as("новая мутирующая ручка должна попасть либо в RECORDED, либо в NOT_RECORDED с причиной")
                .isEqualTo(declared);
    }
}
