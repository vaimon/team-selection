package ru.sfedu.teamselection.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Аутентификация server-to-server вызовов по статическому ключу в заголовке.
 * Ничего не отклоняет сам: при несовпадении просто не аутентифицирует запрос, и
 * решение остаётся за цепочкой (401 из её entry point).
 */
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    public static final String API_KEY_HEADER = "X-Api-Key";

    private static final String PRINCIPAL = "integration";
    private static final String AUTHORITY = "ROLE_INTEGRATION";

    private final String expectedKey;

    public ApiKeyAuthFilter(String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String presentedKey = request.getHeader(API_KEY_HEADER);
        if (matches(presentedKey)) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            PRINCIPAL, null, List.of(new SimpleGrantedAuthority(AUTHORITY))));
        } else {
            log.warn("Integration API key rejected: uri={}, keyPresented={}",
                    request.getRequestURI(), presentedKey != null);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Ненастроенный ключ не должен означать «ключ не нужен»: с пустым секретом
     * фильтр не пропускает никого, включая вызов с пустым заголовком.
     * Сравнение постоянного времени — ключ длинный и сравнивается на каждом вызове.
     */
    private boolean matches(String presentedKey) {
        if (expectedKey == null || expectedKey.isEmpty() || presentedKey == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedKey.getBytes(StandardCharsets.UTF_8),
                presentedKey.getBytes(StandardCharsets.UTF_8));
    }
}
