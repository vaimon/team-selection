package ru.sfedu.teamselection.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.sfedu.teamselection.dto.SelectionHistoryDto;
import ru.sfedu.teamselection.repository.SelectionDailyStatsRepository;

/**
 * Набор по дням для графика в обзоре (#49).
 */
@RequiredArgsConstructor
@Service
public class SelectionHistoryService {

    private final TrackService trackService;
    private final SelectionDailyStatsRepository repository;
    private final SelectionDailyStatsService statsService;
    private final Clock clock;

    public SelectionHistoryDto history() {
        throw new UnsupportedOperationException("#49: not implemented yet");
    }
}
