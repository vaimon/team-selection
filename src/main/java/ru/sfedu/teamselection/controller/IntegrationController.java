package ru.sfedu.teamselection.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sfedu.teamselection.dto.integration.IntegrationRosterDto;
import ru.sfedu.teamselection.dto.integration.IntegrationTrackDto;
import ru.sfedu.teamselection.service.integration.IntegrationRosterService;

/**
 * Server-to-server API для смежной системы. Живёт под собственным префиксом, а не
 * под /api/v1: краевой nginx проксирует наружу только /api/v1, поэтому эти ручки
 * доступны лишь изнутри внутренней сети. Аутентификация — статический ключ, см.
 * {@link ru.sfedu.teamselection.config.IntegrationSecurityConfig}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(IntegrationController.BASE_PATH)
public class IntegrationController {
    public static final String BASE_PATH = "/api/integration/v1";

    private final IntegrationRosterService integrationRosterService;

    @GetMapping("/tracks")
    public List<IntegrationTrackDto> getTracks() {
        return integrationRosterService.findTracks();
    }

    @GetMapping("/tracks/{trackId}/roster")
    public IntegrationRosterDto getRoster(@PathVariable Long trackId) {
        return integrationRosterService.findRoster(trackId);
    }
}
