package ru.sfedu.teamselection.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.sfedu.teamselection.service.security.CurrentAuthoritiesResolver;

/**
 * Replaces the roles captured at login with the ones in the DB for this request only (the session keeps the
 * original authentication). Non-role authorities from the identity provider (OIDC scopes) are kept.
 */
public class CurrentAuthoritiesFilter extends OncePerRequestFilter {
    private static final String ROLE_PREFIX = "ROLE_";

    private final CurrentAuthoritiesResolver resolver;

    public CurrentAuthoritiesFilter(CurrentAuthoritiesResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2AuthenticationToken token) {
            resolver.resolve(token.getPrincipal().getAttribute("email")).ifPresent(roles -> {
                Set<GrantedAuthority> authorities = new HashSet<>(roles);
                token.getAuthorities().stream()
                        .filter(authority -> !authority.getAuthority().startsWith(ROLE_PREFIX))
                        .forEach(authorities::add);

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new OAuth2AuthenticationToken(
                        token.getPrincipal(), authorities, token.getAuthorizedClientRegistrationId()));
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
