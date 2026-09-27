package ru.sfedu.teamselection.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.sfedu.teamselection.domain.SelectionCounts;

/**
 * Счётчики набора по дням (#49): считает их сейчас и записывает за сегодня.
 */
@RequiredArgsConstructor
@Service
public class SelectionDailyStatsService {

    public SelectionCounts countsFor(Long trackId) {
        throw new UnsupportedOperationException("#49: not implemented yet");
    }

    public void recordToday(Long trackId) {
        throw new UnsupportedOperationException("#49: not implemented yet");
    }
}
