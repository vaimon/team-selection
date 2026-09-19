package ru.sfedu.teamselection.smoke;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import ru.sfedu.teamselection.BasicTestContainerTest;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Without the smoke profile there is no sign-in by email at all. Asked by a signed-in student with
 * a valid CSRF token — the caller who could otherwise turn into anyone — the path is simply not
 * there.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SmokeLoginAbsentTest extends BasicTestContainerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginIsNotThereWithoutTheProfile() throws Exception {
        Cookie token = mockMvc.perform(get("/api/v1/users/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post(SmokeLoginController.LOGIN_PATH)
                        .with(user("student").roles("STUDENT"))
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@smoke.test\"}"))
                .andExpect(status().isNotFound());
    }
}
