package ru.sfedu.teamselection.repository.specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.BasicTestContainerTest;
import ru.sfedu.teamselection.TeamSelectionApplication;
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.Team;
import ru.sfedu.teamselection.domain.TeamComposition;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.repository.StudentRepository;
import ru.sfedu.teamselection.repository.TeamRepository;
import ru.sfedu.teamselection.repository.TrackRepository;
import ru.sfedu.teamselection.service.TeamService;

/**
 * «Есть ли здесь место для этого курса» написано дважды: в Java — {@link TeamComposition#canJoin},
 * на который опирается каждый путь вступления, и в SQL — фильтром поиска. Тест проверяет не
 * примеры, а то, что это одно правило: для каждой команды и обоих курсов поиск обязан вернуть
 * ровно те команды, в которые Java пустила бы такого студента.
 *
 * <p>Собственный трек, а не сидовый: так набор команд в выборке известен целиком.
 */
@SpringBootTest(classes = TeamSelectionApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
@ActiveProfiles("test")
@TestPropertySource("/application-test.yml")
class TeamSearchPlacesTest extends BasicTestContainerTest {

    @Autowired
    private TeamService teamService;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private TrackRepository trackRepository;

    private Track track;
    private List<Team> teams;

    @BeforeEach
    void seed() {
        track = trackRepository.save(Track.builder()
                .name("Набор для проверки мест")
                .firstYearTarget(3)
                .secondYearTarget(3)
                .build());

        teams = List.of(
                // Без участников этот хелпер команду не строит: captain_id ссылается на студента,
                // а тимлид берётся из состава.
                team("только тимлид", 1, 0, null, null),
                // Границу «мест ровно ноль» держат эти две: 3 из 3 по своему курсу.
                team("первый курс занят", 3, 0, null, null),
                team("второй курс занят", 0, 3, null, null),
                team("собрана", 3, 3, null, null),
                // Перебор цели. Зажим нуля в Java для сравнения «меньше» алгебраически невидим,
                // так что данными его не различить — случай здесь ради полноты состояний.
                team("перебор по первому курсу", 4, 1, null, null),
                // Своя цель вместо трековой, по обоим курсам: у остальных команд обе цели берутся
                // из трека, и перепутанное поле в coalesce прошло бы незамеченным.
                team("свой потолок по первому курсу", 1, 0, 1, null),
                team("свой потолок по второму курсу", 0, 1, null, 1)
        );
    }

    @Test
    void searchAgreesWithCanJoin() {
        for (int course : new int[] {1, 2}) {
            Set<String> allowedByJava = teams.stream()
                    .filter(team -> TeamComposition.of(team).canJoin(course))
                    .map(Team::getName)
                    .collect(Collectors.toSet());

            Set<String> foundBySearch = teamService
                    .search(null, track.getId(), null, null, null, course, Pageable.unpaged())
                    .stream()
                    .map(Team::getName)
                    .collect(Collectors.toSet());

            Assertions.assertEquals(allowedByJava, foundBySearch, "курс " + course);
        }
    }

    /**
     * Каталог спрашивает оба фильтра сразу: «есть свободные места» и «есть место для меня». Два
     * коррелированных подзапроса в одном запросе должны ужиться.
     */
    @Test
    void composesWithTheIsFullFilter() {
        Set<String> expected = teams.stream()
                .filter(team -> !TeamComposition.of(team).complete())
                .filter(team -> TeamComposition.of(team).canJoin(1))
                .map(Team::getName)
                .collect(Collectors.toSet());

        Set<String> found = teamService
                .search(null, track.getId(), false, null, null, 1, Pageable.unpaged())
                .stream()
                .map(Team::getName)
                .collect(Collectors.toSet());

        Assertions.assertEquals(expected, found);
    }

    @Test
    void withoutTheFilterEveryTeamIsReturned() {
        Set<String> found = teamService
                .search(null, track.getId(), null, null, null, null, Pageable.unpaged())
                .stream()
                .map(Team::getName)
                .collect(Collectors.toSet());

        Assertions.assertEquals(
                teams.stream().map(Team::getName).collect(Collectors.toSet()),
                found
        );
    }

    private Team team(String name, int firstYears, int secondYears, Integer firstTarget, Integer secondTarget) {
        List<Student> members = new ArrayList<>();
        for (int i = 0; i < firstYears; i++) {
            members.add(student(1));
        }
        for (int i = 0; i < secondYears; i++) {
            members.add(student(2));
        }

        return teamRepository.saveAndFlush(Team.builder()
                .name(name)
                .currentTrack(track)
                .firstYearTarget(firstTarget)
                .secondYearTarget(secondTarget)
                // captain_id is NOT NULL with a foreign key: a team always has a lead, and the lead
                // is one of its members.
                .captainId(members.get(0).getId())
                .students(members)
                .build());
    }

    private Student student(int course) {
        return studentRepository.save(Student.builder()
                .course(course)
                .currentTrack(track)
                .build());
    }
}
