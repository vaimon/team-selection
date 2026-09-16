package ru.sfedu.teamselection.config;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import ru.sfedu.teamselection.config.security.CurrentAuthoritiesFilter;
import ru.sfedu.teamselection.service.security.CurrentAuthoritiesResolver;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import ru.sfedu.teamselection.config.security.SimpleAuthenticationSuccessHandler;
import ru.sfedu.teamselection.service.security.AzureOidcUserService;
import ru.sfedu.teamselection.service.security.Oauth2UserService;
import ru.sfedu.teamselection.util.CustomAuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final Oauth2UserService oauth2UserService;
    private final AzureOidcUserService oidcUserService;
    private final SimpleAuthenticationSuccessHandler simpleAuthenticationSuccessHandler;
    private final CurrentAuthoritiesResolver currentAuthoritiesResolver;
    private static final String ADMIN_ROLE_NAME = "ROLE_ADMIN";
    public static final String LOGOUT_URL = "/api/v1/auth/logout";

    @Value("${frontend.url}")
    private String frontendUrl;
    @Value("${frontend.login.url}")
    private String frontendLoginUrl;

    @SuppressWarnings("checkstyle:MultipleStringLiterals")
    @Order(2)
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // The token has to be written on every response, otherwise the SPA has nothing to send
        // back: with Spring Security 6 deferred tokens the repository is never touched until
        // something reads the token, and the cookie is simply not there. A null attribute name
        // opts out of that deferral. The plain (non-XOR) token is enough here — it is never
        // rendered into a response body, so BREACH does not apply.
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler)
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/login", "/registration").anonymous()
                        .requestMatchers(HttpMethod.DELETE).hasAuthority(ADMIN_ROLE_NAME)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                // No session limit: a laptop and a phone are the normal case, and logging in on one
                // used to drop the other. The registry itself stays — UserSessionService expires
                // sessions through it, and expiredUrl is what sends such a session back to login.
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry())
                        .expiredUrl("/login?expired")
                )
                .logout(logout -> logout
                        .logoutUrl(LOGOUT_URL)
                        .deleteCookies("JSESSIONID", "SessionId")
                        .logoutSuccessUrl(frontendUrl + "/login")
                )
                .oauth2Login(login -> login
                        .loginPage("/oauth2/authorization/azure")
                        .userInfoEndpoint(endpoint -> endpoint
                                .userService(oauth2UserService)
                                .oidcUserService(oidcUserService)
                        )
                        .successHandler(simpleAuthenticationSuccessHandler)
                )
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(new CustomAuthenticationEntryPoint(frontendLoginUrl))
                )
                // roles are read from the DB per request, see CurrentAuthoritiesFilter
                .addFilterBefore(new CurrentAuthoritiesFilter(currentAuthoritiesResolver), AuthorizationFilter.class);
        return http.build();
    }



    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowCredentials(true); // Required for session/cookie-based auth
        corsConfiguration.setAllowedOrigins(List.of(frontendUrl)); // Explicitly define allowed origin
        corsConfiguration.setAllowedHeaders(Arrays.asList(
                "Origin", "Content-Type", "Accept", "Authorization", "X-Requested-With", "X-XSRF-TOKEN"
        ));
        // Headers you expose to the frontend
        corsConfiguration.setExposedHeaders(Arrays.asList("Content-Type", "Authorization"));
        corsConfiguration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        return source;
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
    }
}

