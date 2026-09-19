package ru.sfedu.teamselection.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import ru.sfedu.teamselection.BasicTestContainerTest;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The track routes as the whole application serves them. TrackController used to prefix the full
 * paths of the generated TrackApi with its own class-level path, so the real app answered
 * /api/v1/tracks/api/v1/tracks/current and 404 on /api/v1/tracks/current. The slice test in
 * TrackControllerTest resolves the mapping differently and never saw it; only a full context does.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TrackRoutesTest extends BasicTestContainerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void currentSelectionIsServedAtItsDocumentedPath() throws Exception {
        mockMvc.perform(get("/api/v1/tracks/current").with(user("student").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void theDoubledPathIsGone() throws Exception {
        mockMvc.perform(get("/api/v1/tracks/api/v1/tracks/current").with(user("student").roles("STUDENT")))
                .andExpect(status().isNotFound());
    }
}
