package ru.sfedu.teamselection.smoke;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Lets an anonymous caller reach the smoke login. The main chain is left exactly as prod runs it;
 * this one matches the login path only and keeps the same cookie-based CSRF check, so the script
 * has to do the handshake the SPA does.
 */
@Configuration
@Profile(SmokeProfileGuard.PROFILE)
public class SmokeSecurityConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain smokeLoginChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        http
                .securityMatcher(SmokeLoginController.LOGIN_PATH)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler)
                )
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
