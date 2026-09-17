package ru.sfedu.teamselection.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.BasicTestContainerTest;
import ru.sfedu.teamselection.TeamSelectionApplication;
import ru.sfedu.teamselection.domain.Team;
import ru.sfedu.teamselection.repository.ApplicationRepository;
import ru.sfedu.teamselection.repository.StudentRepository;
import ru.sfedu.teamselection.repository.TeamRepository;

/**
 * Админские правки студента (#10): предупреждение о переборе состава и удаление регистрации.
 *
 * <p>Отдельный класс, а не дополнение к StudentServiceTest, по вполне конкретной причине: там
 * {@code studentRepository} подменён шпионом, у которого {@code delete} застаблен на "ничего не
 * делать", а {@code save} подставляет id=100. Настоящее удаление в том классе не проверить, и
 * правки состава там пришлось бы откатывать вручную.
 *
 * <p>Команда 1 из сида: тимлид — студент 2 (первый курс), участник — студент 12 (второй курс).
 * Студент 4 свободен.
 */
@SpringBootTest(classes = TeamSelectionApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
@ActiveProfiles("test")
@TestPropertySource("/application-test.yml")
class StudentAdminFixesTest extends BasicTestContainerTest {
    private static final Long TEAM = 1L;
    private static final Long FIRST_YEAR_MEMBER = 2L;
    private static final Long SECOND_YEAR_MEMBER = 12L;
    private static final Long STUDENT_WITHOUT_TEAM = 4L;
    /** Студент 6: без команды, но с неотвеченной заявкой в сиде. */
    private static final Long STUDENT_WITH_APPLICATION = 6L;

    @Autowired
    private StudentService studentService;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private ApplicationRepository applicationRepository;

    private Team team() {
        return teamRepository.findById(TEAM).orElseThrow();
    }

    @Test
    void aTeamOverItsFirstYearTargetWarnsAndNamesTheTeam() {
        Team team = team();
        team.setFirstYearTarget(0);
        teamRepository.save(team);

        String warning = studentService.compositionWarning(FIRST_YEAR_MEMBER);

        Assertions.assertNotNull(warning);
        Assertions.assertTrue(warning.contains(team.getName()), warning);
        Assertions.assertTrue(warning.contains("первокурсник"), warning);
    }

    @Test
    void aTeamOverItsSecondYearTargetSaysSoAboutTheOlderStudents() {
        Team team = team();
        team.setSecondYearTarget(0);
        teamRepository.save(team);

        String warning = studentService.compositionWarning(SECOND_YEAR_MEMBER);

        Assertions.assertNotNull(warning);
        Assertions.assertTrue(warning.contains("старшекурсник"), warning);
    }

    @Test
    void aCompositionWithinTheTargetsWarnsAboutNothing() {
        Assertions.assertNull(studentService.compositionWarning(FIRST_YEAR_MEMBER));
    }

    @Test
    void aStudentWithoutATeamNeverWarns() {
        Assertions.assertNull(studentService.compositionWarning(STUDENT_WITHOUT_TEAM));
    }

    /**
     * Перебор именно по другому курсу студента не касается, но команда всё равно вне цели, поэтому
     * предупреждение приходит любому её участнику — администратор правит команду, а не человека.
     */
    @Test
    void theWarningIsAboutTheTeamNotAboutTheStudentWhoWasEdited() {
        Team team = team();
        team.setSecondYearTarget(0);
        teamRepository.save(team);

        Assertions.assertNotNull(studentService.compositionWarning(FIRST_YEAR_MEMBER));
    }

    /**
     * Удаление лишней регистрации — прямой пункт #10. У студента 6 в сиде висит заявка, а внешний
     * ключ applications.student_id объявлен без каскада: до этой задачи удаление падало на нём.
     */
    @Test
    void deletingAStudentWhoHasApplicationsWorks() {
        Assertions.assertTrue(applicationRepository.findAll().stream()
                .anyMatch(application -> application.getStudent().getId().equals(STUDENT_WITH_APPLICATION)));

        Assertions.assertDoesNotThrow(() -> {
            studentService.delete(STUDENT_WITH_APPLICATION);
            // flush обязателен: без него нарушение внешнего ключа всплыло бы только на коммите
            studentRepository.flush();
        });

        Assertions.assertTrue(studentRepository.findById(STUDENT_WITH_APPLICATION).isEmpty());
        Assertions.assertTrue(applicationRepository.findAll().stream()
                .noneMatch(application -> application.getStudent().getId().equals(STUDENT_WITH_APPLICATION)));
    }
}
