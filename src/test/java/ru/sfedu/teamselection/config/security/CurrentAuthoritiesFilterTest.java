package ru.sfedu.teamselection.config.security;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import ru.sfedu.teamselection.service.security.CurrentAuthoritiesResolver;
import static org.assertj.core.api.Assertions.assertThat;

class CurrentAuthoritiesFilterTest {
    private final CurrentAuthoritiesResolver resolver = Mockito.mock(CurrentAuthoritiesResolver.class);
    private final CurrentAuthoritiesFilter underTest = new CurrentAuthoritiesFilter(resolver);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static OAuth2AuthenticationToken loggedInAs(String... authorities) {
        List<GrantedAuthority> granted = java.util.Arrays.stream(authorities)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
        var principal = new DefaultOAuth2User(granted, Map.of("sub", "1", "email", "st@sfedu.ru"), "sub");
        return new OAuth2AuthenticationToken(principal, granted, "azure");
    }

    private Set<String> authoritiesAfterFilter() throws Exception {
        underTest.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
        Authentication actual = SecurityContextHolder.getContext().getAuthentication();
        return actual.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void rolesFromTheSessionAreReplacedByCurrentOnesAndScopesAreKept() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(loggedInAs("ROLE_USER", "SCOPE_openid"));
        Mockito.when(resolver.resolve("st@sfedu.ru")).thenReturn(Optional.of(Set.of(
                new SimpleGrantedAuthority("ROLE_STUDENT"),
                new SimpleGrantedAuthority(CurrentAuthoritiesResolver.PARTICIPANT)
        )));

        assertThat(authoritiesAfterFilter())
                .containsExactlyInAnyOrder("ROLE_STUDENT", "ROLE_PARTICIPANT", "SCOPE_openid");
    }

    @Test
    void unknownAccountKeepsTheSessionAuthorities() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(loggedInAs("ROLE_USER"));
        Mockito.when(resolver.resolve("st@sfedu.ru")).thenReturn(Optional.empty());

        assertThat(authoritiesAfterFilter()).containsExactly("ROLE_USER");
    }

    @Test
    void anonymousRequestIsLeftAlone() throws Exception {
        underTest.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        Mockito.verifyNoInteractions(resolver);
    }
}
