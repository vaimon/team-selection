package ru.sfedu.teamselection.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.sfedu.teamselection.repository.TrackRepository;

/**
 * Переписывает строку за сегодня после каждого изменения, попавшего в историю (#49).
 *
 * <p>После коммита, а не внутри транзакции изменения: часть сервисов пишет историю до самого
 * изменения — роспуск записывается, пока имя команды ещё можно прочитать, — и пересчёт внутри
 * транзакции увидел бы команду живой.
 *
 * <p>Сбой пересчёта не должен долетать до того, кто менял состав: его изменение уже закоммичено,
 * а ответ 500 сказал бы обратное. Строка догонит состав при следующем изменении.
 *
 * <p>Одно действие иногда пишет в историю две записи (перенос тимлида — «новый тимлид» и
 * «перемещён») и пересчитывает счётчики дважды. Это безвредно: запись — upsert того же дня.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SelectionDailyStatsListener {

    private final SelectionDailyStatsService statsService;
    private final TrackRepository trackRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivityRecorded(ActivityRecorded event) {
        if (event.trackId() == null) {
            return;
        }
        try {
            boolean active = trackRepository.findByActiveTrue()
                    .map(track -> track.getId().equals(event.trackId()))
                    .orElse(false);
            if (active) {
                statsService.recordToday(event.trackId());
            }
        } catch (RuntimeException e) {
            log.warn("Could not record today's counts for selection {}", event.trackId(), e);
        }
    }
}
