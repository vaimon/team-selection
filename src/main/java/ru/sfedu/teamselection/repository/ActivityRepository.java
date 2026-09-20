package ru.sfedu.teamselection.repository;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.sfedu.teamselection.domain.activity.ActivityEntry;

@Repository
public interface ActivityRepository extends JpaRepository<ActivityEntry, Long> {

    /**
     * Фильтр истории: незаданный параметр ничего не сужает. По команде находит и те записи, где она
     * вторая сторона перемещения, иначе «что было с этой командой» отвечало бы половину правды.
     */
    @Query("""
            select e
            from ActivityEntry e
            where (:teamId is null or e.teamId = :teamId or e.relatedTeamId = :teamId)
              and (:studentId is null or e.studentId = :studentId)
              and (:actorUserId is null or e.actorUserId = :actorUserId)
              and (cast(:from as timestamp) is null or e.createdAt >= :from)
              and (cast(:to as timestamp) is null or e.createdAt <= :to)
            order by e.createdAt desc, e.id desc
            """)
    Page<ActivityEntry> search(@Param("teamId") Long teamId,
                               @Param("studentId") Long studentId,
                               @Param("actorUserId") Long actorUserId,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to,
                               Pageable pageable);

    long deleteByCreatedAtBefore(LocalDateTime threshold);
}
