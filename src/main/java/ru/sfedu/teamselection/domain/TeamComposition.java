package ru.sfedu.teamselection.domain;

import java.util.List;

/**
 * Per-year make-up of a team against its effective targets. Always derived from the current members,
 * never stored: a stored flag goes stale as soon as targets change.
 *
 * @param firstYears       members on course 1
 * @param secondYears      members on course 2 and above
 * @param firstYearTarget  team override, otherwise the track target
 * @param secondYearTarget team override, otherwise the track target
 */
public record TeamComposition(int firstYears, int secondYears, int firstYearTarget, int secondYearTarget) {

    public static TeamComposition of(Team team) {
        List<Student> members = team.getStudents() == null ? List.of() : team.getStudents();
        int firstYears = (int) members.stream().filter(s -> isFirstYear(s.getCourse())).count();
        Track track = team.getCurrentTrack();
        return new TeamComposition(
                firstYears,
                members.size() - firstYears,
                team.getFirstYearTarget() != null ? team.getFirstYearTarget() : track.getFirstYearTarget(),
                team.getSecondYearTarget() != null ? team.getSecondYearTarget() : track.getSecondYearTarget()
        );
    }

    public static boolean isFirstYear(Integer course) {
        return course != null && course == 1;
    }

    public int size() {
        return firstYears + secondYears;
    }

    public int firstYearPlacesLeft() {
        return Math.max(0, firstYearTarget - firstYears);
    }

    public int secondYearPlacesLeft() {
        return Math.max(0, secondYearTarget - secondYears);
    }

    public boolean complete() {
        return firstYears >= firstYearTarget && secondYears >= secondYearTarget;
    }

    public boolean overTarget() {
        return firstYears > firstYearTarget || secondYears > secondYearTarget;
    }

    public boolean canJoin(Integer course) {
        return isFirstYear(course) ? firstYearPlacesLeft() > 0 : secondYearPlacesLeft() > 0;
    }
}
