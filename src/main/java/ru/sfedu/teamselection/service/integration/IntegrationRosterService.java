package ru.sfedu.teamselection.service.integration;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.Team;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.dto.integration.IntegrationRosterDto;
import ru.sfedu.teamselection.dto.integration.IntegrationStudentDto;
import ru.sfedu.teamselection.dto.integration.IntegrationTeamDto;
import ru.sfedu.teamselection.dto.integration.IntegrationTrackDto;
import ru.sfedu.teamselection.exception.NotFoundException;
import ru.sfedu.teamselection.repository.TeamRepository;
import ru.sfedu.teamselection.repository.TrackRepository;
import ru.sfedu.teamselection.repository.specification.TeamSpecification;
import ru.sfedu.teamselection.service.StudentService;

/**
 * Читающий сервис интеграционного API: отдаёт состав трека внешней системе.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationRosterService {
    /**
     * teams.captain_id не nullable и по умолчанию хранит -1 — это «капитана нет»,
     * а не ссылка на студента с таким id.
     */
    private static final long NO_CAPTAIN = -1L;

    private final TrackRepository trackRepository;
    private final TeamRepository teamRepository;
    private final StudentService studentService;

    @Transactional(readOnly = true)
    public List<IntegrationTrackDto> findTracks() {
        return trackRepository.findAll(Sort.by(Sort.Direction.DESC, "startDate"))
                .stream()
                .map(this::toTrackDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public IntegrationRosterDto findRoster(Long trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException("Трек не найден, id=" + trackId));

        List<Team> teams = teamRepository.findAll(TeamSpecification.byTrack(trackId), Sort.by("name"));
        List<Student> students = studentService.findAllByTrack(trackId, Sort.by("user.fio"));

        log.info("Integration roster pulled: trackId={}, teams={}, students={}",
                trackId, teams.size(), students.size());

        return IntegrationRosterDto.builder()
                .track(toTrackDto(track))
                .teams(teams.stream().map(this::toTeamDto).toList())
                .students(students.stream().map(this::toStudentDto).toList())
                .build();
    }

    private IntegrationTrackDto toTrackDto(Track track) {
        return IntegrationTrackDto.builder()
                .id(track.getId())
                .name(track.getName())
                .type(track.getType() == null ? null : track.getType().name())
                .startDate(track.getStartDate())
                .endDate(track.getEndDate())
                .build();
    }

    private IntegrationTeamDto toTeamDto(Team team) {
        Long captainId = team.getCaptainId();
        return IntegrationTeamDto.builder()
                .id(team.getId())
                .name(team.getName())
                .projectDescription(team.getProjectDescription())
                .projectType(team.getProjectType() == null ? null : team.getProjectType().getName())
                .captainStudentId(captainId == null || captainId == NO_CAPTAIN ? null : captainId)
                .isFull(team.getIsFull())
                .quantityOfStudents(team.getQuantityOfStudents())
                .build();
    }

    private IntegrationStudentDto toStudentDto(Student student) {
        Team currentTeam = student.getCurrentTeam();
        return IntegrationStudentDto.builder()
                .id(student.getId())
                .fio(student.getUser().getFio())
                .email(student.getUser().getEmail())
                .course(student.getCourse())
                .groupNumber(student.getGroupNumber())
                .hasTeam(student.getHasTeam())
                .isCaptain(student.getIsCaptain())
                .teamId(currentTeam == null ? null : currentTeam.getId())
                .build();
    }
}
