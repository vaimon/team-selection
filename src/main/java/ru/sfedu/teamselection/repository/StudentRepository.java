package ru.sfedu.teamselection.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.sfedu.teamselection.domain.Student;


@Repository
public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

    Student findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndCurrentTrackActiveTrue(Long userId);
    // Здесь была deactivateCaptainsWithExpiredTracks: ночная джоба распускала все команды набора в
    // первую же полночь после end_date. После закрытия набора команды больше не трогают автоматически —
    // неполные составы разбирает администратор (#6).


    @Query("""
  select s
  from Student s
  where 
    ( :teamId  is not null and s.currentTeam.id = :teamId )
 OR ( :trackId is not null and s.currentTrack.id = :trackId and s.hasTeam = false )
""")
    List<Student> findFreeOrInTeam(@Param("trackId") Long trackId,
                                   @Param("teamId" ) Long teamId);

}
