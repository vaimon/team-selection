package ru.sfedu.teamselection.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sfedu.teamselection.domain.Track;
import ru.sfedu.teamselection.dto.integration.IntegrationHandOverDto;
import ru.sfedu.teamselection.dto.integration.IntegrationRosterDto;
import ru.sfedu.teamselection.dto.integration.IntegrationTrackDto;
import ru.sfedu.teamselection.service.TrackService;
import ru.sfedu.teamselection.service.integration.IntegrationRosterService;

/**
 * Server-to-server API для смежной системы. Живёт под собственным префиксом, а не
 * под /api/v1: краевой nginx проксирует наружу только /api/v1, поэтому эти ручки
 * доступны лишь изнутри внутренней сети. Аутентификация — статический ключ, см.
 * {@link ru.sfedu.teamselection.config.IntegrationSecurityConfig}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(IntegrationController.BASE_PATH)
public class IntegrationController {
    public static final String BASE_PATH = "/api/integration/v1";

    private final IntegrationRosterService integrationRosterService;
    private final TrackService trackService;

    @GetMapping("/tracks")
    public List<IntegrationTrackDto> getTracks() {
        return integrationRosterService.findTracks();
    }

    @GetMapping("/tracks/{trackId}/roster")
    public IntegrationRosterDto getRoster(@PathVariable Long trackId) {
        return integrationRosterService.findRoster(trackId);
    }

    /**
     * Core зовёт после применения импорта состава: дальше набор только для чтения (#15).
     * Идемпотентно — core может повторить вызов, если первый ответ не дошёл.
     */
    @PostMapping("/tracks/{trackId}/handover")
    public IntegrationHandOverDto handOver(@PathVariable Long trackId) {
        log.info("Integration hand-over requested for track {}", trackId);
        Track track = trackService.handOver(trackId);
        return new IntegrationHandOverDto(track.getId(), track.getHandedOverAt());
    }
}
