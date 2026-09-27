package ru.sfedu.teamselection.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.sfedu.teamselection.domain.stats.SelectionDailyStats;

@Repository
public interface SelectionDailyStatsRepository
        extends JpaRepository<SelectionDailyStats, SelectionDailyStats.Key> {

    List<SelectionDailyStats> findAllByTrackIdOrderByDayAsc(Long trackId);

    /**
     * Одной командой, а не «найти и сохранить»: два изменения состава, закоммиченные в одну секунду,
     * иначе оба решили бы, что строки за сегодня ещё нет, и второе упало бы на первичном ключе.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into selection_daily_stats
                (track_id, day, total_teams, complete_teams, students_in_teams, registered, updated_at)
            values (:trackId, :day, :totalTeams, :completeTeams, :studentsInTeams, :registered, :updatedAt)
            on conflict (track_id, day) do update set
                total_teams = excluded.total_teams,
                complete_teams = excluded.complete_teams,
                students_in_teams = excluded.students_in_teams,
                registered = excluded.registered,
                updated_at = excluded.updated_at
            """)
    void upsert(@Param("trackId") Long trackId,
                @Param("day") LocalDate day,
                @Param("totalTeams") int totalTeams,
                @Param("completeTeams") int completeTeams,
                @Param("studentsInTeams") int studentsInTeams,
                @Param("registered") int registered,
                @Param("updatedAt") LocalDateTime updatedAt);
}
