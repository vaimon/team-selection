package ru.sfedu.teamselection.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import ru.sfedu.teamselection.service.security.CurrentAuthoritiesResolver;

@Component
@RequiredArgsConstructor
public class SimpleAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Value("${frontend.url}")
    private String frontendUrl;

    private final CurrentAuthoritiesResolver currentAuthoritiesResolver;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {
        // The session cookie is the container's business: it was hand-written here as JSESSIONID plus
        // a second copy named SessionId that nothing ever read, which also overrode the cookie
        // attributes configured for the server (SameSite among them) and turned a session cookie into
        // a week-long one.
        OAuth2User oidcUser = (OAuth2User) authentication.getPrincipal();
        String email = oidcUser.getAttribute("email");  // или preferred_username

        redirectStrategy.sendRedirect(request, response, frontendUrl + targetPath(email));
    }


    /**
     * Admins land in the admin area, participants of the current selection in the catalog, everyone else on
     * the participant questionnaire.
     */
    String targetPath(String email) {
        Set<String> roles = currentAuthoritiesResolver.resolve(email).orElse(Set.of()).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        if (roles.contains("ROLE_ADMIN")) {
            return "/admin";
        }
        return roles.contains(CurrentAuthoritiesResolver.PARTICIPANT) ? "/teams" : "/registration";
    }
}
