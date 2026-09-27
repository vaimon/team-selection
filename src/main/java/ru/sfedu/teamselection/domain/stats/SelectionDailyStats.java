package ru.sfedu.teamselection.domain.stats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Счётчики набора на конец дня (#49). Сегодняшняя строка переписывается после каждого изменения
 * состава, так что к полуночи в ней то, с чем день закончился.
 */
@Entity
@Table(name = "selection_daily_stats")
@IdClass(SelectionDailyStats.Key.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectionDailyStats {

    @Id
    @Column(name = "track_id")
    private Long trackId;

    @Id
    @Column(name = "day")
    private LocalDate day;

    @Column(name = "total_teams", nullable = false)
    private int totalTeams;

    @Column(name = "complete_teams", nullable = false)
    private int completeTeams;

    @Column(name = "students_in_teams", nullable = false)
    private int studentsInTeams;

    @Column(name = "registered", nullable = false)
    private int registered;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long trackId;
        private LocalDate day;
    }
}
