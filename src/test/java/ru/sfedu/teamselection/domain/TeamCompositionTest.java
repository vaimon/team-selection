package ru.sfedu.teamselection.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TeamCompositionTest {

    private static Track track(int firstYearTarget, int secondYearTarget) {
        return Track.builder().firstYearTarget(firstYearTarget).secondYearTarget(secondYearTarget).build();
    }

    private static Team team(Track track, int... courses) {
        List<Student> students = new ArrayList<>();
        IntStream.of(courses).forEach(course -> students.add(Student.builder().course(course).build()));
        return Team.builder().currentTrack(track).students(students).build();
    }

    @Test
    void countsCourseOneAsFirstYearAndEverythingElseAsSecondYear() {
        TeamComposition actual = TeamComposition.of(team(track(3, 3), 1, 1, 2, 3, 5));

        Assertions.assertEquals(2, actual.firstYears());
        Assertions.assertEquals(3, actual.secondYears());
        Assertions.assertEquals(5, actual.size());
    }

    @Test
    void placesLeftNeverGoBelowZero() {
        TeamComposition actual = TeamComposition.of(team(track(1, 3), 1, 1, 2));

        Assertions.assertEquals(0, actual.firstYearPlacesLeft());
        Assertions.assertEquals(2, actual.secondYearPlacesLeft());
    }

    @Test
    void completeOnlyWhenBothTargetsAreMet() {
        Assertions.assertFalse(TeamComposition.of(team(track(3, 3), 1, 1, 1, 2, 2)).complete());
        Assertions.assertTrue(TeamComposition.of(team(track(3, 3), 1, 1, 1, 2, 2, 4)).complete());
    }

    @Test
    void canJoinOnlyWhileTheStudentsYearHasPlaces() {
        TeamComposition actual = TeamComposition.of(team(track(3, 3), 1, 1, 1, 2));

        Assertions.assertFalse(actual.canJoin(1));
        Assertions.assertTrue(actual.canJoin(2));
        Assertions.assertTrue(actual.canJoin(4));
    }

    @Test
    void teamOverrideReplacesTrackTargets() {
        Team team = team(track(3, 3), 1, 1, 1, 2);
        team.setFirstYearTarget(4);

        TeamComposition actual = TeamComposition.of(team);

        Assertions.assertEquals(4, actual.firstYearTarget());
        Assertions.assertEquals(3, actual.secondYearTarget());
        Assertions.assertTrue(actual.canJoin(1));
    }

    @Test
    void teamWithoutMembersListIsEmpty() {
        Team team = Team.builder().currentTrack(track(3, 3)).build();

        TeamComposition actual = TeamComposition.of(team);

        Assertions.assertEquals(0, actual.size());
        Assertions.assertFalse(actual.complete());
    }
}
