package ru.sfedu.teamselection.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.Team;
import ru.sfedu.teamselection.domain.TeamComposition;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.dto.board.BoardLeadRequest;
import ru.sfedu.teamselection.dto.board.BoardMemberRow;
import ru.sfedu.teamselection.dto.board.BoardMoveRequest;
import ru.sfedu.teamselection.dto.board.BoardStudentRow;
import ru.sfedu.teamselection.dto.board.BoardTargetsRequest;
import ru.sfedu.teamselection.dto.board.BoardVersionRequest;
import ru.sfedu.teamselection.dto.board.CompositionBoardDto;
import ru.sfedu.teamselection.dto.board.CompositionBoardDto.StudentCard;
import ru.sfedu.teamselection.dto.board.CompositionBoardDto.TeamCard;
import ru.sfedu.teamselection.dto.board.CompositionBoardDto.TeamStatus;
import ru.sfedu.teamselection.enums.ConflictReason;
import ru.sfedu.teamselection.exception.ConflictException;
import ru.sfedu.teamselection.exception.ConstraintViolationException;
import ru.sfedu.teamselection.repository.CompositionBoardRepository;
import ru.sfedu.teamselection.repository.TeamRepository;

/**
 * Доска состава для организатора (#14): полная картина набора и перемещения студентов.
 *
 * <p>Каждое действие сверяет версии затронутых команд с теми, что организатор видел на доске, и
 * возвращает доску заново — со свежими версиями для следующего действия. Сами изменения идут через
 * примитивы TeamService, чтобы флаги has_team, тимлида и закрытие заявок оставались в одном месте.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CompositionBoardService {

    private final TrackService trackService;
    private final TeamService teamService;
    private final StudentService studentService;
    private final TeamRepository teamRepository;
    private final CompositionBoardRepository boardRepository;

    @Transactional(readOnly = true)
    public CompositionBoardDto board() {
        Track track = trackService.getActive();

        Map<Long, List<BoardMemberRow>> rowsByTeam = new LinkedHashMap<>();
        for (BoardMemberRow row : boardRepository.findMemberRows(track.getId())) {
            rowsByTeam.computeIfAbsent(row.teamId(), id -> new ArrayList<>()).add(row);
        }
        List<TeamCard> teams = rowsByTeam.values().stream()
                .map(rows -> teamCard(rows, track))
                .toList();
        List<StudentCard> pool = boardRepository.findPoolRows(track.getId()).stream()
                .map(row -> new StudentCard(row.id(), row.name(), row.course(), row.groupNumber(),
                        Boolean.TRUE.equals(row.isCaptain())))
                .toList();

        return new CompositionBoardDto(
                track.getId(), track.getName(), track.getFirstYearTarget(), track.getSecondYearTarget(), teams, pool);
    }

    private static TeamCard teamCard(List<BoardMemberRow> rows, Track track) {
        BoardMemberRow team = rows.get(0);
        List<StudentCard> members = rows.stream()
                .filter(row -> row.studentId() != null)
                .map(row -> new StudentCard(row.studentId(), row.studentName(), row.course(), row.groupNumber(),
                        Boolean.TRUE.equals(row.isCaptain())))
                .toList();
        int firstYears = (int) members.stream().filter(member -> TeamComposition.isFirstYear(member.course())).count();
        TeamComposition composition = new TeamComposition(
                firstYears,
                members.size() - firstYears,
                Objects.requireNonNullElse(team.firstYearOverride(), track.getFirstYearTarget()),
                Objects.requireNonNullElse(team.secondYearOverride(), track.getSecondYearTarget()));

        return new TeamCard(
                team.teamId(),
                team.teamName(),
                team.version(),
                team.captainId(),
                members,
                composition.firstYears(),
                composition.secondYears(),
                composition.firstYearTarget(),
                composition.secondYearTarget(),
                team.firstYearOverride(),
                team.secondYearOverride(),
                status(composition));
    }

    private static TeamStatus status(TeamComposition composition) {
        if (composition.overTarget()) {
            return TeamStatus.OVER_TARGET;
        }
        return composition.complete() ? TeamStatus.COMPLETE : TeamStatus.INCOMPLETE;
    }

    /**
     * Все проверки идут до первого изменения: отказ по любой из них оставляет обе команды нетронутыми.
     */
    @Transactional
    public CompositionBoardDto move(BoardMoveRequest request, User sender) {
        if (request.fromTeamId() == null && request.toTeamId() == null) {
            throw new ConstraintViolationException("Укажите, откуда или куда перемещается студент");
        }
        if (Objects.equals(request.fromTeamId(), request.toTeamId())) {
            throw new ConstraintViolationException("Студент уже в этой команде");
        }
        Student student = studentService.findByIdOrElseThrow(request.studentId());
        Team from = request.fromTeamId() == null ? null : versioned(request.fromTeamId(), request.fromVersion());
        Team to = request.toTeamId() == null ? null : versioned(request.toTeamId(), request.toVersion());

        if (from == null && Boolean.TRUE.equals(student.getHasTeam())) {
            throw new ConstraintViolationException("Студент состоит в команде — укажите, из какой его перемещают");
        }
        if (to != null && (student.getCurrentTrack() == null
                || !student.getCurrentTrack().getId().equals(to.getCurrentTrack().getId()))) {
            throw new ConstraintViolationException("Студент не участвует в наборе команды «" + to.getName() + "»");
        }
        if (from != null && !isMember(from, student.getId())) {
            throw new ConstraintViolationException("Студент не состоит в команде «" + from.getName() + "»");
        }
        boolean movingTheLead = from != null && from.getCaptainId().equals(student.getId());
        if (movingTheLead) {
            assertSuccessor(from, student, request.newLeadId());
        }
        if (to != null && !request.allowOverTarget() && !TeamComposition.of(to).canJoin(student.getCourse())) {
            throw new ConflictException(ConflictReason.OVER_TARGET,
                    "Команда «" + to.getName() + "» уже набрала цель по курсу студента");
        }

        // Сначала преемник: removeStudentFromTeam отказывает, пока студент числится тимлидом команды.
        if (movingTheLead) {
            teamService.transferCaptaincy(from.getId(), request.newLeadId(), sender);
        }
        if (from != null) {
            teamService.removeStudentFromTeam(from, student);
        }
        if (to != null) {
            teamService.addStudentToTeam(to, student, true, null);
        }
        log.info("Board move by user {}: student {} from team {} to team {}, overTarget={}, newLead={}",
                sender.getId(), student.getId(), request.fromTeamId(), request.toTeamId(),
                request.allowOverTarget(), request.newLeadId());
        return freshBoard();
    }

    private static void assertSuccessor(Team team, Student lead, Long newLeadId) {
        if (team.getStudents().size() == 1) {
            throw new ConflictException(ConflictReason.LEAD_NEEDS_SUCCESSOR,
                    "Тимлид — единственный участник команды «" + team.getName() + "»: распустите команду");
        }
        if (newLeadId == null) {
            throw new ConflictException(ConflictReason.LEAD_NEEDS_SUCCESSOR,
                    "Перемещается тимлид команды «" + team.getName() + "»: выберите нового тимлида");
        }
        if (newLeadId.equals(lead.getId()) || !isMember(team, newLeadId)) {
            throw new ConstraintViolationException("Новым тимлидом может стать только оставшийся участник команды");
        }
    }

    @Transactional
    public CompositionBoardDto setTargets(Long teamId, BoardTargetsRequest request) {
        Team team = versioned(teamId, request.version());

        team.setFirstYearTarget(request.firstYearTarget());
        team.setSecondYearTarget(request.secondYearTarget());
        log.info("Board targets of team {} set to {}/{}", teamId, request.firstYearTarget(), request.secondYearTarget());
        return freshBoard();
    }

    @Transactional
    public CompositionBoardDto changeLead(Long teamId, BoardLeadRequest request, User sender) {
        versioned(teamId, request.version());
        teamService.transferCaptaincy(teamId, request.studentId(), sender);
        log.info("Board lead of team {} changed to student {} by user {}", teamId, request.studentId(), sender.getId());
        return freshBoard();
    }

    @Transactional
    public CompositionBoardDto dissolve(Long teamId, BoardVersionRequest request, User sender) {
        versioned(teamId, request.version());
        teamService.disband(teamId, sender);
        log.info("Board dissolved team {} by user {}", teamId, sender.getId());
        return freshBoard();
    }

    private Team versioned(Long teamId, Long expectedVersion) {
        if (expectedVersion == null) {
            throw new ConstraintViolationException("Не передана версия команды с id `" + teamId + "`");
        }
        Team team = teamService.findByIdOrElseThrow(teamId);
        trackService.assertWritable(team.getCurrentTrack());
        if (!expectedVersion.equals(team.getVersion())) {
            throw new ConflictException(ConflictReason.STALE_VERSION,
                    "Команду «" + team.getName() + "» изменили, пока вы с ней работали. Обновите доску");
        }
        return team;
    }

    private static boolean isMember(Team team, Long studentId) {
        return team.getStudents().stream().anyMatch(member -> member.getId().equals(studentId));
    }

    /** Версии растут при сбросе изменений в базу, поэтому сбрасываем до чтения, а не после. */
    private CompositionBoardDto freshBoard() {
        teamRepository.flush();
        return board();
    }
}
