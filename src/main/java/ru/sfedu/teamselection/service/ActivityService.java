package ru.sfedu.teamselection.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.Team;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.domain.activity.ActivityEntry;
import ru.sfedu.teamselection.domain.application.Application;
import ru.sfedu.teamselection.enums.ActivityAction;
import ru.sfedu.teamselection.repository.ActivityRepository;

/**
 * История действий над составом (#16): кто, что и когда изменил.
 *
 * <p>Записи складываются здесь, а не в интерцепторе: читаемая строка требует старого состояния и
 * имён, а в теле HTTP-запроса нет ни того, ни другого. Все формулировки собраны в одном классе,
 * чтобы сервисы передавали только сущности.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ActivityService {

    private static final String NO_TEAM = "без команды";

    private final ActivityRepository activityRepository;
    private final Clock clock;

    // --- команды ---

    @Transactional
    public void teamCreated(Team team, User actor) {
        record(ActivityAction.TEAM_CREATED, actor, team, null, null,
                "Создана команда «%s»".formatted(name(team)));
    }

    @Transactional
    public void teamUpdated(Team team, User actor) {
        record(ActivityAction.TEAM_UPDATED, actor, team, null, null,
                "Изменено описание команды «%s»".formatted(name(team)));
    }

    @Transactional
    public void teamDisbanded(Team team, User actor) {
        record(ActivityAction.TEAM_DISBANDED, actor, team, null, null,
                "Распущена команда «%s»".formatted(name(team)));
    }

    @Transactional
    public void targetsChanged(Team team, Integer firstYearTarget, Integer secondYearTarget, User actor) {
        record(ActivityAction.TARGETS_CHANGED, actor, team, null, null,
                "Цели команды «%s»: %s курс 1, %s курс 2 и старше".formatted(
                        name(team), target(firstYearTarget), target(secondYearTarget)));
    }

    // --- состав ---

    @Transactional
    public void memberJoined(Team team, Student student, User actor) {
        record(ActivityAction.MEMBER_JOINED, actor, team, null, student,
                "%s в команде «%s»".formatted(name(student), name(team)));
    }

    @Transactional
    public void memberRemoved(Team team, Student student, User actor) {
        record(ActivityAction.MEMBER_REMOVED, actor, team, null, student,
                "%s исключён из команды «%s»".formatted(name(student), name(team)));
    }

    @Transactional
    public void memberLeft(Team team, Student student, User actor) {
        record(ActivityAction.MEMBER_LEFT, actor, team, null, student,
                "%s вышел из команды «%s»".formatted(name(student), name(team)));
    }

    /**
     * Перемещение по доске состава — одна запись, а не пара «исключён» и «добавлен»: организатор
     * сделал одно действие, и в истории оно должно читаться как одно.
     */
    @Transactional
    public void memberMoved(Student student, Team from, Team to, User actor) {
        record(ActivityAction.MEMBER_MOVED, actor, to != null ? to : from, to != null ? from : null, student,
                "%s перенесён из «%s» в «%s»".formatted(
                        name(student),
                        from != null ? name(from) : NO_TEAM,
                        to != null ? name(to) : NO_TEAM));
    }

    @Transactional
    public void leadChanged(Team team, Student newLead, User actor) {
        record(ActivityAction.LEAD_CHANGED, actor, team, null, newLead,
                "Тимлид команды «%s» — %s".formatted(name(team), name(newLead)));
    }

    // --- заявки и ссылка ---

    @Transactional
    public void applicationSent(Application application, User actor) {
        record(ActivityAction.APPLICATION_SENT, actor, application.getTeam(), null, application.getStudent(),
                "Заявка: %s и команда «%s»".formatted(
                        name(application.getStudent()), name(application.getTeam())));
    }

    @Transactional
    public void applicationAnswered(Application application, User actor) {
        record(ActivityAction.APPLICATION_ANSWERED, actor, application.getTeam(), null, application.getStudent(),
                "Заявка %s в команду «%s»: %s".formatted(
                        name(application.getStudent()), name(application.getTeam()), answer(application)));
    }

    @Transactional
    public void joinLinkIssued(Team team, User actor) {
        record(ActivityAction.JOIN_LINK_ISSUED, actor, team, null, null,
                "Выдана ссылка-приглашение в команду «%s»".formatted(name(team)));
    }

    @Transactional
    public void joinLinkDisabled(Team team, User actor) {
        record(ActivityAction.JOIN_LINK_DISABLED, actor, team, null, null,
                "Отключена ссылка-приглашение в команду «%s»".formatted(name(team)));
    }

    // --- студенты и пользователи ---

    @Transactional
    public void questionnaireFilled(Student student, User actor) {
        record(ActivityAction.QUESTIONNAIRE_FILLED, actor, null, null, student,
                "%s заполнил анкету участника".formatted(name(student)));
    }

    @Transactional
    public void studentUpdated(Student student, User actor) {
        record(ActivityAction.STUDENT_UPDATED, actor, student.getCurrentTeam(), null, student,
                "Изменены данные студента %s".formatted(name(student)));
    }

    @Transactional
    public void studentDeleted(Student student, User actor) {
        record(ActivityAction.STUDENT_DELETED, actor, student.getCurrentTeam(), null, student,
                "Удалена регистрация студента %s".formatted(name(student)));
    }

    @Transactional
    public void roleAssigned(User target, String roleName, User actor) {
        record(ActivityAction.ROLE_ASSIGNED, actor, null, null, null,
                "%s: роль %s".formatted(target.getFio(), roleName));
    }

    @Transactional
    public void userDeactivated(User target, User actor) {
        record(ActivityAction.USER_DEACTIVATED, actor, null, null, null,
                "Отключён доступ: %s".formatted(target.getFio()));
    }

    // --- набор ---

    @Transactional
    public void selectionSettingsChanged(Track track, User actor) {
        record(ActivityAction.SELECTION_SETTINGS_CHANGED, actor, track, null,
                "Изменены настройки набора «%s»".formatted(track.getName()));
    }

    @Transactional
    public void selectionStarted(Track track, User actor) {
        record(ActivityAction.SELECTION_STARTED, actor, track, null,
                "Начат набор «%s»".formatted(track.getName()));
    }

    /** Передачу обычно отмечает core по ключу интеграции — тогда автора у записи нет. */
    @Transactional
    public void handedOver(Track track, User actor) {
        record(ActivityAction.HANDED_OVER, actor, track, null,
                "Состав набора «%s» передан в кабинет ПД%s".formatted(
                        track.getName(), actor == null ? " (вызов из кабинета ПД)" : ""));
    }

    @Transactional
    public void handOverCancelled(Track track, User actor) {
        record(ActivityAction.HANDOVER_CANCELLED, actor, track, null,
                "Отменена передача набора «%s»".formatted(track.getName()));
    }

    // --- чтение и чистка ---

    @Transactional
    public long purge(int retentionDays) {
        LocalDateTime threshold = LocalDateTime.now(clock).minusDays(retentionDays);
        long removed = activityRepository.deleteByCreatedAtBefore(threshold);
        log.info("Activity history purged: {} entries older than {}", removed, threshold);
        return removed;
    }

    private void record(ActivityAction action, User actor, Team team, Team relatedTeam, Student student,
                        String summary) {
        Track track = team != null ? team.getCurrentTrack()
                : student != null ? student.getCurrentTrack()
                : null;
        save(action, actor, track, team, relatedTeam, student, summary);
    }

    private void record(ActivityAction action, User actor, Track track, Team team, String summary) {
        save(action, actor, track, team, null, null, summary);
    }

    private void save(ActivityAction action, User actor, Track track, Team team, Team relatedTeam, Student student,
                      String summary) {
        activityRepository.save(ActivityEntry.builder()
                .action(action)
                .actorUserId(actor != null ? actor.getId() : null)
                .actorName(actor != null ? actor.getFio() : null)
                .actorEmail(actor != null ? actor.getEmail() : null)
                .trackId(track != null ? track.getId() : null)
                .teamId(team != null ? team.getId() : null)
                .relatedTeamId(relatedTeam != null ? relatedTeam.getId() : null)
                .studentId(student != null ? student.getId() : null)
                .summary(summary)
                .createdAt(LocalDateTime.now(clock))
                .build());
    }

    private static String name(Team team) {
        return Objects.requireNonNullElse(team.getName(), "без названия");
    }

    private static String name(Student student) {
        return student.getUser() != null ? student.getUser().getFio() : "студент " + student.getId();
    }

    private static String answer(Application application) {
        return switch (application.status()) {
            case ACCEPTED -> "принята";
            case REJECTED -> "отклонена";
            case CANCELLED -> "отозвана";
            case SENT -> "снова в ожидании";
        };
    }

    private static String target(Integer target) {
        return target != null ? String.valueOf(target) : "по набору";
    }
}
