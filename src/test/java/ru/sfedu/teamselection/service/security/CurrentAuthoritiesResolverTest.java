package ru.sfedu.teamselection.service.security;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import ru.sfedu.teamselection.BasicTestContainerTest;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class CurrentAuthoritiesResolverTest extends BasicTestContainerTest {
    @Autowired
    private CurrentAuthoritiesResolver underTest;

    private Set<String> resolve(String email) {
        return underTest.resolve(email).orElseThrow().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    @Test
    void studentRegisteredForTheActiveTrackIsParticipant() {
        // user 3 = student 2, registered on track 1 (active)
        assertThat(resolve(emailOfUser3())).containsExactlyInAnyOrder("ROLE_STUDENT", "ROLE_PARTICIPANT");
    }

    @Test
    @Sql(statements = "UPDATE students SET current_track_id = 2 WHERE user_id = 3")
    void studentRegisteredForAPreviousSelectionIsNotParticipant() {
        assertThat(resolve(emailOfUser3())).containsExactly("ROLE_STUDENT");
    }

    @Test
    void adminWithoutQuestionnaireIsAdminOnly() {
        assertThat(resolve("admin_mail")).containsExactly("ROLE_ADMIN");
    }

    @Test
    @Sql(statements = "UPDATE users SET role_id = 3 WHERE id = 3")
    void roleChangeInTheDatabaseIsVisibleImmediately() {
        assertThat(resolve(emailOfUser3())).contains("ROLE_ADMIN");
    }

    @Test
    void unknownAccountResolvesToNothing() {
        assertThat(underTest.resolve("nobody@sfedu.ru")).isEmpty();
        assertThat(underTest.resolve(null)).isEmpty();
    }

    private static String emailOfUser3() {
        return "user3_mail";
    }
}
