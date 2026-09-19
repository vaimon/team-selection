package ru.sfedu.teamselection.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.sfedu.teamselection.domain.User;
import ru.sfedu.teamselection.repository.UserRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The smoke profile as the Playwright journey meets it: sign in by email through the same CSRF
 * handshake the SPA does, then use the ordinary API with the session that comes back.
 *
 * <p>Runs on its own database: the seed switches the active selection, which would pull the
 * ground from under the tests that rely on the demo data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "smoke"})
@TestPropertySource("/application-test.yml")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SmokeProfileTest {
    private static final String LOGIN = "/api/v1/smoke/login";
    private static final String TOKEN_COOKIE = "XSRF-TOKEN";
    private static final String TOKEN_HEADER = "X-XSRF-TOKEN";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("team-selection-smoke")
            .withUsername("TestUser")
            .withPassword("1234");

    @DynamicPropertySource
    static void jdbcProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void seededTeamLeadSignsInAsAParticipant() throws Exception {
        MvcResult login = loginAs("lead@smoke.test")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect").value("/teams"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/api/v1/users/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("lead@smoke.test"));
    }

    @Test
    void seededAdminLandsInTheAdminArea() throws Exception {
        loginAs("admin@smoke.test")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect").value("/admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void aFreshEmailIsANewStudentWithoutAQuestionnaire() throws Exception {
        loginAs("newcomer@smoke.test")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect").value("/registration"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void refusesWithoutTheCsrfToken() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"lead@smoke.test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void refusesADisabledAccount() throws Exception {
        loginAs("disabled@smoke.test").andExpect(status().isOk());
        User user = userRepository.findByEmailFetchRole("disabled@smoke.test").orElseThrow();
        user.setIsEnabled(false);
        userRepository.save(user);

        loginAs("disabled@smoke.test").andExpect(status().isForbidden());
    }

    @Test
    void refusesAnInvalidEmail() throws Exception {
        loginAs("not-an-email").andExpect(status().isBadRequest());
    }

    @Test
    void seedOpensASelectionWithThreePlusThreePlaces() throws Exception {
        MockHttpSession session = sessionOf("first@smoke.test");

        mockMvc.perform(get("/api/v1/tracks/current").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Смоук-набор"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.windowState").value("OPEN"))
                .andExpect(jsonPath("$.firstYearTarget").value(3))
                .andExpect(jsonPath("$.secondYearTarget").value(3));
    }

    @Test
    void seedHasAFreeFirstYearAndAFreeSecondYear() throws Exception {
        assertCourseAndNoTeam("first@smoke.test", 1);
        assertCourseAndNoTeam("second@smoke.test", 2);
        assertCourseAndNoTeam("lead@smoke.test", 2);
    }

    private void assertCourseAndNoTeam(String email, int course) throws Exception {
        MockHttpSession session = sessionOf(email);
        String studentId = mockMvc.perform(get("/api/v1/students/me").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/v1/students/" + studentId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.course").value(course))
                .andExpect(jsonPath("$.has_team").value(false));
    }

    private MockHttpSession sessionOf(String email) throws Exception {
        MvcResult result = loginAs(email).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private ResultActions loginAs(String email) throws Exception {
        Cookie token = mockMvc.perform(get("/api/v1/users/me")).andReturn().getResponse().getCookie(TOKEN_COOKIE);
        assertThat(token).as("the backend hands out the CSRF cookie").isNotNull();
        JsonNode body = json.createObjectNode().put("email", email);

        return mockMvc.perform(post(LOGIN)
                .cookie(token)
                .header(TOKEN_HEADER, token.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }
}
