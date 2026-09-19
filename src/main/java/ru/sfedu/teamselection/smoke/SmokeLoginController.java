package ru.sfedu.teamselection.smoke;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.sfedu.teamselection.config.security.SimpleAuthenticationSuccessHandler;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.exception.ForbiddenException;
import ru.sfedu.teamselection.service.UserService;

/**
 * Sign-in by email for the scripted journey smoke, where SFedU SSO cannot be driven.
 *
 * <p>It ends up where a real SSO login ends up — the same first-login rules, the same kind of
 * authentication in the session, the same landing page — so everything after it (per-request
 * roles, CSRF, the current user) is the production path, not a test double of it.
 */
@Slf4j
@RestController
@Profile(SmokeProfileGuard.PROFILE)
@RequiredArgsConstructor
public class SmokeLoginController {
    public static final String LOGIN_PATH = "/api/v1/smoke/login";
    /** The registration a real login comes through; the audit and role filters only care about the token type. */
    private static final String REGISTRATION_ID = "azure";
    private static final Duration SESSION_TOKEN_LIFETIME = Duration.ofHours(8);

    private final UserService userService;
    private final SimpleAuthenticationSuccessHandler successHandler;
    private final SessionRegistry sessionRegistry;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @PostMapping(LOGIN_PATH)
    public ResponseEntity<SmokeLoginResponse> login(
            @Valid @RequestBody SmokeLoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String email = body.email().trim();
        String fio = body.fio() == null || body.fio().isBlank() ? email.substring(0, email.indexOf('@')) : body.fio();
        User user = userService.findOrCreateByEmail(email, fio, null);
        if (!user.isEnabled()) {
            throw new ForbiddenException(
                    "Аккаунт отключен. По вопросам возвращения доступа обращаться к администратору ресурса."
            );
        }

        Instant now = Instant.now();
        OidcIdToken idToken = OidcIdToken.withTokenValue("smoke")
                .subject(email)
                .issuedAt(now)
                .expiresAt(now.plus(SESSION_TOKEN_LIFETIME))
                .claim("email", email)
                .claim("name", user.getFio())
                .build();
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));
        DefaultOidcUser principal = new DefaultOidcUser(authorities, idToken, "email");
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new OAuth2AuthenticationToken(principal, authorities, REGISTRATION_ID));
        SecurityContextHolder.setContext(context);
        // As on a real login: a session that existed before sign-in gets a new id, and the session is
        // registered, so a role change expires it through UserSessionService just as it would in prod.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        securityContextRepository.saveContext(context, request, response);
        sessionRegistry.registerNewSession(request.getSession().getId(), principal);

        String redirect = successHandler.targetPath(email);
        log.info("Smoke login as {} ({}), landing on {}", email, user.getRole().getName(), redirect);
        return ResponseEntity.ok(new SmokeLoginResponse(user.getId(), user.getRole().getName(), redirect));
    }

    public record SmokeLoginRequest(@NotBlank @Email String email, String fio) {
    }

    public record SmokeLoginResponse(Long userId, String role, String redirect) {
    }
}
