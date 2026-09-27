package ru.sfedu.teamselection.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.domain.SelectionCounts;
import ru.sfedu.teamselection.repository.SelectionDailyStatsRepository;
import ru.sfedu.teamselection.repository.StudentRepository;
import ru.sfedu.teamselection.repository.TeamRepository;

/**
 * Счётчики набора по дням (#49): считает их сейчас и записывает за сегодня.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SelectionDailyStatsService {

    private final StudentRepository studentRepository;
    private final TeamRepository teamRepository;
    private final SelectionDailyStatsRepository repository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SelectionCounts countsFor(Long trackId) {
        return SelectionCounts.of(
                studentRepository.findAllByCurrentTrackId(trackId),
                teamRepository.findAllByCurrentTrackId(trackId));
    }

    /**
     * Своя транзакция, а не общая: вызывается после коммита изменения, когда та транзакция уже
     * закончена и участвовать в ней нельзя.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordToday(Long trackId) {
        SelectionCounts counts = countsFor(trackId);
        LocalDate today = LocalDate.now(clock);
        repository.upsert(trackId, today, counts.totalTeams(), counts.completeTeams(),
                counts.studentsInTeams(), counts.registered(), LocalDateTime.now(clock));
        log.debug("Selection {} on {}: {}", trackId, today, counts);
    }
}
