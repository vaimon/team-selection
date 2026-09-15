package ru.sfedu.teamselection.config.security;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import ru.sfedu.teamselection.service.security.CurrentAuthoritiesResolver;
import static org.assertj.core.api.Assertions.assertThat;

class SimpleAuthenticationSuccessHandlerTest {
    private final CurrentAuthoritiesResolver resolver = Mockito.mock(CurrentAuthoritiesResolver.class);
    private final SimpleAuthenticationSuccessHandler underTest = new SimpleAuthenticationSuccessHandler(resolver);

    private void currentRoles(String... roles) {
        Set<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
        Mockito.when(resolver.resolve("st@sfedu.ru")).thenReturn(Optional.of(authorities));
    }

    @Test
    void adminGoesToTheAdminArea() {
        currentRoles("ROLE_ADMIN");

        assertThat(underTest.targetPath("st@sfedu.ru")).isEqualTo("/admin");
    }

    @Test
    void participantGoesToTheCatalog() {
        currentRoles("ROLE_STUDENT", CurrentAuthoritiesResolver.PARTICIPANT);

        assertThat(underTest.targetPath("st@sfedu.ru")).isEqualTo("/teams");
    }

    @Test
    void studentWithoutQuestionnaireForThisSelectionGoesToRegistration() {
        currentRoles("ROLE_STUDENT");

        assertThat(underTest.targetPath("st@sfedu.ru")).isEqualTo("/registration");
    }
}
