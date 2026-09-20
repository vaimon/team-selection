package ru.sfedu.teamselection.service;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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
import ru.sfedu.teamselection.domain.Student;
import ru.sfedu.teamselection.domain.Team;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.domain.activity.ActivityEntry;
import ru.sfedu.teamselection.enums.ActivityAction;
import ru.sfedu.teamselection.repository.ActivityRepository;
import ru.sfedu.teamselection.repository.StudentRepository;
import ru.sfedu.teamselection.repository.TeamRepository;
import ru.sfedu.teamselection.repository.TrackRepository;
import ru.sfedu.teamselection.repository.UserRepository;

/**
 * Записи истории (#16): что именно увидит организатор и что останется после чистки.
 *
 * <p>Сид: команда 1 «Tech Titans» — тимлид студент 2, участник студент 12; студент 4 свободен.
 */
@SpringBootTest(classes = TeamSelectionApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
@ActiveProfiles("test")
@TestPropertySource("/application-test.yml")
class ActivityServiceTest extends BasicTestContainerTest {

    @Autowired
    private ActivityService underTest;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Team team;
    private Student student;
    private User admin;

    @BeforeEach
    void seedObjects() {
        team = teamRepository.findById(1L).orElseThrow();
        student = studentRepository.findById(4L).orElseThrow();
        admin = userRepository.findById(1L).orElseThrow();
    }

    private ActivityEntry theOnlyEntry() {
        List<ActivityEntry> entries = activityRepository.findAll();
        Assertions.assertEquals(1, entries.size(), "ожидалась ровно одна запись");
        return entries.get(0);
    }

    @Test
    void anEntryNamesTheActorTheTargetAndTheSelection() {
        underTest.memberRemoved(team, student, admin);

        ActivityEntry entry = theOnlyEntry();
        Assertions.assertEquals(ActivityAction.MEMBER_REMOVED, entry.getAction());
        Assertions.assertEquals(admin.getId(), entry.getActorUserId());
        Assertions.assertEquals(admin.getFio(), entry.getActorName());
        Assertions.assertEquals(admin.getEmail(), entry.getActorEmail());
        Assertions.assertEquals(team.getId(), entry.getTeamId());
        Assertions.assertEquals(student.getId(), entry.getStudentId());
        Assertions.assertEquals(team.getCurrentTrack().getId(), entry.getTrackId());
        Assertions.assertNotNull(entry.getCreatedAt());
    }

    @Test
    void theSummaryReadsAsASentenceWithBothNames() {
        underTest.memberRemoved(team, student, admin);

        Assertions.assertEquals(
                "%s исключён из команды «%s»".formatted(student.getUser().getFio(), team.getName()),
                theOnlyEntry().getSummary());
    }

    /** Перемещение — одна запись, и по ней находится и команда-источник, и команда-цель. */
    @Test
    void aMoveIsOneEntryThatMentionsBothTeams() {
        Team other = teamRepository.findById(2L).orElseThrow();

        underTest.memberMoved(student, other, team, admin);

        ActivityEntry entry = theOnlyEntry();
        Assertions.assertEquals(
                "%s перенесён из «%s» в «%s»".formatted(student.getUser().getFio(), other.getName(), team.getName()),
                entry.getSummary());
        Assertions.assertEquals(team.getId(), entry.getTeamId());
        Assertions.assertEquals(other.getId(), entry.getRelatedTeamId());
    }

    @Test
    void aMoveToThePoolSaysSoInsteadOfNamingATeam() {
        underTest.memberMoved(student, team, null, admin);

        Assertions.assertEquals(
                "%s перенесён из «%s» в «без команды»".formatted(student.getUser().getFio(), team.getName()),
                theOnlyEntry().getSummary());
    }

    /** Передачу отмечает core по ключу интеграции: автора у записи нет, и это видно в тексте. */
    @Test
    void anEntryWithoutAnActorSaysWhereItCameFrom() {
        underTest.handedOver(trackRepository.findByActiveTrue().orElseThrow(), null);

        ActivityEntry entry = theOnlyEntry();
        Assertions.assertNull(entry.getActorUserId());
        Assertions.assertTrue(entry.getSummary().contains("кабинет"), entry.getSummary());
    }

    @Test
    void theHistoryIsFilteredByTeamStudentActorAndPeriod() {
        Team other = teamRepository.findById(2L).orElseThrow();
        underTest.memberRemoved(team, student, admin);
        underTest.teamUpdated(other, userRepository.findById(3L).orElseThrow());

        Assertions.assertEquals(1, search(team.getId(), null, null).size());
        Assertions.assertEquals(1, search(null, student.getId(), null).size());
        Assertions.assertEquals(1, search(null, null, admin.getId()).size());
        Assertions.assertEquals(2, search(null, null, null).size());
    }

    private List<ActivityEntry> search(Long teamId, Long studentId, Long actorUserId) {
        return activityRepository.search(teamId, studentId, actorUserId, null, null,
                org.springframework.data.domain.PageRequest.of(0, 50)).getContent();
    }

    /** Чистка забирает старые записи и не трогает свежие. */
    @Test
    void purgeRemovesOnlyEntriesOlderThanTheRetention() {
        underTest.memberRemoved(team, student, admin);
        activityRepository.flush();
        jdbcTemplate.update("INSERT INTO activity_log (action, summary, created_at) VALUES (?, ?, ?)",
                ActivityAction.TEAM_UPDATED.name(), "древняя запись", LocalDateTime.now().minusDays(500));

        long removed = underTest.purge(400);

        Assertions.assertEquals(1, removed);
        Assertions.assertEquals(1, activityRepository.findAll().size());
        Assertions.assertEquals(ActivityAction.MEMBER_REMOVED, activityRepository.findAll().get(0).getAction());
    }
}
