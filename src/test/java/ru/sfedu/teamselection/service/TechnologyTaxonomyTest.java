package ru.sfedu.teamselection.service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.BasicTestContainerTest;
import ru.sfedu.teamselection.TeamSelectionApplication;
import ru.sfedu.teamselection.domain.Technology;
import ru.sfedu.teamselection.dto.TechnologyDto;
import ru.sfedu.teamselection.repository.TechnologyRepository;
import ru.sfedu.teamselection.repository.TrackRepository;

/**
 * Справочник технологий после чистки (#12): типы проекта и роли из него ушли вместе со связями.
 */
@SpringBootTest(classes = TeamSelectionApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
@ActiveProfiles("test")
@TestPropertySource("/application-test.yml")
class TechnologyTaxonomyTest extends BasicTestContainerTest {

    /** Ровно те имена, которые убрала миграция V2.10. */
    private static final Set<String> NOT_TECHNOLOGIES = Set.of(
            "web", "mobile", "desktop", "crossplatform", "gamedev", "analytics", "frontend", "backend");

    @Autowired
    private TechnologyRepository technologyRepository;
    @Autowired
    private StudentService studentService;
    @Autowired
    private TeamService teamService;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long activeTrackId() {
        return trackRepository.findByActiveTrue().orElseThrow().getId();
    }

    private static Set<String> lowerCased(Collection<TechnologyDto> technologies) {
        return technologies.stream()
                .map(technology -> technology.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    @Test
    void theDictionaryHasNoProjectTypesOrRolesLeft() {
        List<String> left = technologyRepository.findAll().stream()
                .map(Technology::getName)
                .filter(name -> NOT_TECHNOLOGIES.contains(name.toLowerCase(Locale.ROOT)))
                .toList();

        Assertions.assertEquals(List.of(), left);
    }

    @Test
    void realTechnologiesAndSkillsAreUntouched() {
        Set<String> names = technologyRepository.findAll().stream()
                .map(technology -> technology.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        Assertions.assertTrue(names.containsAll(Set.of("java", "react", "ml", "devops")), names.toString());
    }

    /**
     * Связи именно сняты, а не остались у живых команд. Что «висящих» связей не бывает вовсе,
     * проверять нечем: внешний ключ не дал бы удалить технологию, миграция упала бы целиком.
     */
    @Test
    void theSeededTeamsAndStudentsLostThoseTags() {
        String stillTagged = """
                select count(*) from (
                    select technology_id from teams_technologies
                    union all
                    select technology_id from students_technologies
                ) links
                join technologies t on t.id = links.technology_id
                where lower(t.name) in (%s)
                """.formatted(NOT_TECHNOLOGIES.stream().map(name -> "'" + name + "'").collect(Collectors.joining(", ")));

        Assertions.assertEquals(0L, jdbcTemplate.queryForObject(stillTagged, Long.class));
    }

    @Test
    void theFiltersNoLongerOfferThem() {
        Set<String> studentFilters = lowerCased(
                studentService.getSearchOptionsStudents(activeTrackId()).getTechnologies());
        Set<String> teamFilters = lowerCased(
                teamService.getSearchOptionsTeams(activeTrackId()).getTechnologies());

        Assertions.assertTrue(Collections.disjoint(studentFilters, NOT_TECHNOLOGIES), studentFilters.toString());
        Assertions.assertTrue(Collections.disjoint(teamFilters, NOT_TECHNOLOGIES), teamFilters.toString());
    }
}
