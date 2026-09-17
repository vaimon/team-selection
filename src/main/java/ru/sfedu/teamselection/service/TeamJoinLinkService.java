package ru.sfedu.teamselection.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.Team;
import ru.sfedu.teamselection.domain.TeamComposition;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.dto.team.TeamJoinPreviewDto;
import ru.sfedu.teamselection.enums.JoinRefusalReason;
import ru.sfedu.teamselection.enums.SelectionWindowState;
import ru.sfedu.teamselection.exception.ForbiddenException;
import ru.sfedu.teamselection.exception.NotFoundException;
import ru.sfedu.teamselection.repository.StudentRepository;
import ru.sfedu.teamselection.repository.TeamRepository;

/**
 * Ссылка-приглашение в команду (#13): тимлид делится ссылкой, приглашённый входит одним шагом.
 *
 * <p>Отдельного одобрения не спрашиваем — ссылку дал сам тимлид. Все остальные правила входа те же,
 * что и у принятой заявки, поэтому сам вход делегируется в {@link TeamService#addStudentToTeam}.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TeamJoinLinkService {
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TeamRepository teamRepository;
    private final TeamService teamService;
    private final TrackService trackService;
    private final StudentRepository studentRepository;
    private final SelectionWindowService selectionWindowService;

    /**
     * Текущий токен команды или {@code null}, если ссылки нет.
     */
    @Transactional(readOnly = true)
    public String currentToken(Long teamId, User sender) {
        Team team = teamService.findByIdOrElseThrow(teamId);
        teamService.assertCaptainOrAdmin(team, sender);
        return team.getJoinToken();
    }

    /**
     * Выпускает новую ссылку, гася предыдущую. Только тимлид: кого звать в команду — его решение,
     * администратор ссылку лишь видит и гасит.
     */
    @Transactional
    public String issue(Long teamId, User sender) {
        Team team = teamService.findByIdOrElseThrow(teamId);
        if (!teamService.isCaptain(team, sender)) {
            throw new ForbiddenException("Ссылку-приглашение выпускает только тимлид команды");
        }
        selectionWindowService.assertStudentMutationAllowed(sender);
        trackService.assertWritable(team.getCurrentTrack());

        team.setJoinToken(newToken());
        teamRepository.save(team);
        log.info("Join link issued for team {} by user {}", teamId, sender.getId());
        return team.getJoinToken();
    }

    /**
     * Гасит ссылку. Окном намеренно не ограничено: отозвать утёкшую ссылку нужно и после закрытия
     * набора, а отключение ничего не открывает.
     */
    @Transactional
    public void disable(Long teamId, User sender) {
        Team team = teamService.findByIdOrElseThrow(teamId);
        teamService.assertCaptainOrAdmin(team, sender);

        team.setJoinToken(null);
        teamRepository.save(team);
        log.info("Join link disabled for team {} by user {}", teamId, sender.getId());
    }

    /**
     * Что видно по ссылке. Доступно любому вошедшему, поэтому имя тимлида заполняется только для
     * участников набора.
     */
    @Transactional(readOnly = true)
    public TeamJoinPreviewDto preview(String token, User caller) {
        Team team = byTokenOrElseThrow(token);
        TeamComposition composition = TeamComposition.of(team);
        JoinRefusalReason refusal = refusalFor(team, caller);
        Student callerStudent = studentOf(caller);

        return TeamJoinPreviewDto.builder()
                .teamId(team.getId())
                .teamName(team.getName())
                .captainName(isParticipant(caller) ? captainName(team) : null)
                .firstYears(composition.firstYears())
                .firstYearTarget(composition.firstYearTarget())
                .secondYears(composition.secondYears())
                .secondYearTarget(composition.secondYearTarget())
                .canJoin(refusal == null)
                .refusalReason(refusal)
                .build();
    }

    /**
     * Вход по ссылке. Проверки состава, набора и отмену прочих заявок берёт на себя
     * {@link TeamService#addStudentToTeam}.
     */
    @Transactional
    public Team join(String token, User caller) {
        selectionWindowService.assertStudentMutationAllowed(caller);
        Team team = byTokenOrElseThrow(token);

        Student student = studentOf(caller);
        if (student == null) {
            throw new ForbiddenException("Чтобы войти в команду, сначала заполните анкету участника набора");
        }
        return teamService.addStudentToTeam(team, student, false, null);
    }

    private Team byTokenOrElseThrow(String token) {
        return teamRepository.findByJoinToken(token)
                .orElseThrow(() -> new NotFoundException("Ссылка-приглашение недействительна"));
    }

    /**
     * Первая подходящая причина отказа, в порядке от общего к частному: сначала окно набора, затем
     * кто человек такой, затем где он уже состоит, и лишь в конце — есть ли место для его курса.
     *
     * @return причина, по которой этот человек войти не может, либо {@code null}, если может
     */
    private JoinRefusalReason refusalFor(Team team, User caller) {
        if (selectionWindowService.stateOf(team.getCurrentTrack()) != SelectionWindowState.OPEN) {
            return JoinRefusalReason.WINDOW_CLOSED;
        }
        Student student = studentOf(caller);
        if (student == null || student.getCurrentTrack() == null) {
            return JoinRefusalReason.NOT_A_PARTICIPANT;
        }
        if (!Objects.equals(student.getCurrentTrack().getId(), team.getCurrentTrack().getId())) {
            return JoinRefusalReason.ANOTHER_SELECTION;
        }
        if (team.getStudents().stream().anyMatch(member -> member.getId().equals(student.getId()))) {
            return JoinRefusalReason.ALREADY_IN_THIS_TEAM;
        }
        if (Boolean.TRUE.equals(student.getHasTeam())) {
            return JoinRefusalReason.ALREADY_IN_A_TEAM;
        }
        if (!TeamComposition.of(team).canJoin(student.getCourse())) {
            return JoinRefusalReason.NO_PLACES_FOR_THE_YEAR;
        }
        return null;
    }

    private Student studentOf(User caller) {
        return studentRepository.existsByUserId(caller.getId())
                ? studentRepository.findByUserId(caller.getId())
                : null;
    }

    /** Тот же источник истины, что и у ROLE_PARTICIPANT: анкета заполнена для текущего набора. */
    private boolean isParticipant(User caller) {
        return studentRepository.existsByUserIdAndCurrentTrackActiveTrue(caller.getId());
    }

    private String captainName(Team team) {
        return studentRepository.findById(team.getCaptainId())
                .map(captain -> captain.getUser().getFio())
                .orElse(null);
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
