package ru.sfedu.teamselection.util.validation;

import java.time.LocalDate;

/**
 * Окно набора. Реализуют DTO, которые его несут, чтобы {@link ValidDateRange} проверял их все,
 * а не только тот, под который был написан первым.
 */
public interface DateRange {
    LocalDate getStartDate();

    LocalDate getEndDate();
}
