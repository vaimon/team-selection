package ru.sfedu.teamselection.config.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Фильтр сам ничего не отклоняет — он либо аутентифицирует запрос, либо нет.
 * Поэтому проверяется именно наличие Authentication в контексте.
 */
public class ApiKeyAuthFilterTest {
    private static final String KEY = "expected-key";
    private static final String URI = "/api/integration/v1/tracks";

    @AfterEach
    public void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void authenticatesWhenTheKeyMatches() throws Exception {
        assertNotNull(runWithHeader(KEY, KEY), "валидный ключ должен аутентифицировать запрос");
    }

    @Test
    public void doesNotAuthenticateWithoutTheHeader() throws Exception {
        assertNull(runWithHeader(KEY, null));
    }

    @Test
    public void doesNotAuthenticateOnAWrongKey() throws Exception {
        assertNull(runWithHeader(KEY, "wrong-key"));
    }

    /**
     * Ненастроенный ключ — не «ключ не нужен»: пустой заголовок не должен совпасть
     * с пустым секретом, иначе выкатка без секрета открыла бы ручки всем.
     */
    @Test
    public void doesNotAuthenticateWhenNoKeyIsConfigured() throws Exception {
        assertNull(runWithHeader("", ""));
        assertNull(runWithHeader(null, ""));
    }

    private Object runWithHeader(String configuredKey, String presentedKey) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", URI);
        if (presentedKey != null) {
            request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, presentedKey);
        }
        new ApiKeyAuthFilter(configuredKey)
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
