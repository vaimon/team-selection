package ru.sfedu.teamselection.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.sfedu.teamselection.config.security.ApiKeyAuthFilter;

/**
 * Отдельная цепочка для server-to-server интеграции. Существующая цепочка не
 * меняется: у неё нет securityMatcher, поэтому она остаётся последней и ловит всё
 * остальное — включая /api/integration/**, если эта цепочка вдруг исчезнет
 * (её правило /api/** требует аутентификации, то есть отказ, а не проход).
 */
@Configuration
public class IntegrationSecurityConfig {
    private static final String INTEGRATION_PATH = "/api/integration/**";

    @Bean
    @Order(1)
    public SecurityFilterChain integrationFilterChain(
            HttpSecurity http,
            @Value("${integration.api-key:}") String apiKey) throws Exception {
        http
                .securityMatcher(INTEGRATION_PATH)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(new ApiKeyAuthFilter(apiKey), UsernamePasswordAuthenticationFilter.class)
                // Без явного entry point неаутентифицированный запрос получил бы 403
                // от дефолтного Http403ForbiddenEntryPoint — для отсутствующего
                // ключа честный ответ 401.
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                );
        return http.build();
    }
}
