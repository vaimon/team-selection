package ru.sfedu.teamselection.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.enums.SelectionWindowState;
import ru.sfedu.teamselection.exception.ForbiddenException;

/**
 * Окно набора: до открытия и после закрытия студенты и капитаны ничего не меняют.
 *
 * <p>Администратор освобождён — после закрытия он и разбирает команды, не набравшие состав.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SelectionWindowService {
    private static final String ADMIN_ROLE = "ADMIN";
    private static final DateTimeFormatter HUMAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final Clock clock;
    private final TrackService trackService;

    public SelectionWindowState stateOf(Track track) {
        return SelectionWindowState.of(track.getStartDate(), track.getEndDate(), LocalDate.now(clock));
    }

    /**
     * Пропускает мутацию, только если текущий набор открыт прямо сейчас либо её делает администратор.
     *
     * @param sender пользователь, от имени которого идёт изменение
     * @throws ForbiddenException если набор ещё не открыт или уже закрыт
     * @throws ru.sfedu.teamselection.exception.ConflictException если состав набора передан в кабинет ПД
     */
    public void assertStudentMutationAllowed(User sender) {
        // Передача проверяется первой: она запирает и администратора, а к моменту передачи окно обычно
        // уже закрыто — иначе студент получил бы «набор закрыт» вместо того, куда теперь идти (#15).
        Track active = trackService.getActive();
        trackService.assertNotHandedOver(active);
        if (ADMIN_ROLE.equals(sender.getRole().getName())) {
            return;
        }
        SelectionWindowState state = stateOf(active);
        if (state != SelectionWindowState.OPEN) {
            log.info("Mutation refused for user {}: selection '{}' is {} (window {} - {})",
                    sender.getId(), active.getName(), state, active.getStartDate(), active.getEndDate());
            throw new ForbiddenException(refusalMessage(active, state));
        }
    }

    private static String refusalMessage(Track track, SelectionWindowState state) {
        if (state == SelectionWindowState.NOT_OPEN) {
            return track.getStartDate() == null
                    ? "Набор «%s» ещё не открыт: не назначена дата начала.".formatted(track.getName())
                    : "Набор «%s» ещё не открыт, приём начнётся %s."
                            .formatted(track.getName(), track.getStartDate().format(HUMAN_DATE));
        }
        return track.getEndDate() == null
                ? "Набор «%s» недоступен: не назначена дата окончания.".formatted(track.getName())
                : "Набор «%s» закрыт %s, изменения больше не принимаются."
                        .formatted(track.getName(), track.getEndDate().format(HUMAN_DATE));
    }
}
