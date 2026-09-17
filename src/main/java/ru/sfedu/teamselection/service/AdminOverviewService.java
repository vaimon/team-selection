package ru.sfedu.teamselection.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.Team;
import ru.sfedu.teamselection.domain.TeamComposition;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.domain.application.Application;
import ru.sfedu.teamselection.domain.application.ApplicationType;
import ru.sfedu.teamselection.dto.AdminOverviewDto;
import ru.sfedu.teamselection.enums.ApplicationStatus;
import ru.sfedu.teamselection.repository.ApplicationRepository;
import ru.sfedu.teamselection.repository.StudentRepository;
import ru.sfedu.teamselection.repository.TeamRepository;

/**
 * Состояние набора для организатора (#10).
 *
 * <p>Считается в памяти по трём выборкам, а не шестью count-запросами: масштаб набора — около
 * двухсот студентов и трёх десятков команд, и укомплектованность всё равно выводится из состава
 * ({@link TeamComposition}), а не хранится.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AdminOverviewService {

    private final TrackService trackService;
    private final StudentRepository studentRepository;
    private final TeamRepository teamRepository;
    private final ApplicationRepository applicationRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminOverviewDto overview(int pendingOlderThanDays) {
        Track track = trackService.getActive();
        List<Student> students = studentRepository.findAllByCurrentTrackId(track.getId());
        List<Team> teams = teamRepository.findAllByCurrentTrackId(track.getId());
        List<Application> pending = applicationRepository.findAllByTeamCurrentTrackIdAndStatus(
                track.getId(), ApplicationStatus.SENT.toString());

        return new AdminOverviewDto(
                track.getId(),
                track.getName(),
                countStudents(students),
                countTeams(teams),
                countApplications(pending, pendingOlderThanDays)
        );
    }

    private static AdminOverviewDto.Students countStudents(List<Student> students) {
        int firstYear = (int) students.stream()
                .filter(student -> TeamComposition.isFirstYear(student.getCourse()))
                .count();
        int withTeam = (int) students.stream()
                .filter(student -> Boolean.TRUE.equals(student.getHasTeam()))
                .count();
        int firstYearWithoutTeam = (int) students.stream()
                .filter(student -> TeamComposition.isFirstYear(student.getCourse()))
                .filter(student -> !Boolean.TRUE.equals(student.getHasTeam()))
                .count();
        int withoutTeam = students.size() - withTeam;

        return new AdminOverviewDto.Students(
                students.size(),
                firstYear,
                students.size() - firstYear,
                withTeam,
                withoutTeam,
                firstYearWithoutTeam,
                withoutTeam - firstYearWithoutTeam
        );
    }

    private static AdminOverviewDto.Teams countTeams(List<Team> teams) {
        int complete = (int) teams.stream()
                .filter(team -> TeamComposition.of(team).complete())
                .count();
        return new AdminOverviewDto.Teams(teams.size(), complete, teams.size() - complete);
    }

    private AdminOverviewDto.Applications countApplications(List<Application> pending, int olderThanDays) {
        LocalDateTime staleBefore = LocalDateTime.now(clock).minusDays(olderThanDays);

        return new AdminOverviewDto.Applications(
                count(pending, ApplicationType.REQUEST, null),
                count(pending, ApplicationType.INVITE, null),
                count(pending, ApplicationType.REQUEST, staleBefore),
                count(pending, ApplicationType.INVITE, staleBefore),
                olderThanDays
        );
    }

    private static int count(List<Application> pending, ApplicationType type, LocalDateTime staleBefore) {
        return (int) pending.stream()
                .filter(application -> application.getType() == type)
                .filter(application -> staleBefore == null || application.getCreatedAt().isBefore(staleBefore))
                .count();
    }
}
