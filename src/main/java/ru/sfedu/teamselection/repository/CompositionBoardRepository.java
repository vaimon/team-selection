package ru.sfedu.teamselection.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import ru.sfedu.teamselection.domain.Team;
import ru.sfedu.teamselection.dto.board.BoardMemberRow;
import ru.sfedu.teamselection.dto.board.BoardStudentRow;

/**
 * Чтение доски состава двумя запросами на весь набор, сколько бы в нём ни было команд (#14).
 */
public interface CompositionBoardRepository extends Repository<Team, Long> {

    @Query("""
            select new ru.sfedu.teamselection.dto.board.BoardMemberRow(
                t.id, t.name, t.version, t.captainId, t.firstYearTarget, t.secondYearTarget,
                s.id, u.fio, s.course, s.groupNumber, s.isCaptain)
            from Team t
            left join t.students s
            left join s.user u
            where t.currentTrack.id = :trackId
            order by t.name, t.id, u.fio, s.id
            """)
    List<BoardMemberRow> findMemberRows(@Param("trackId") Long trackId);

    @Query("""
            select new ru.sfedu.teamselection.dto.board.BoardStudentRow(
                s.id, u.fio, s.course, s.groupNumber, s.isCaptain)
            from Student s
            join s.user u
            where s.currentTrack.id = :trackId and s.hasTeam = false
            order by u.fio, s.id
            """)
    List<BoardStudentRow> findPoolRows(@Param("trackId") Long trackId);
}
