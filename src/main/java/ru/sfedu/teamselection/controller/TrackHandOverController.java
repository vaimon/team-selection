package ru.sfedu.teamselection.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sfedu.teamselection.config.security.Access;
import ru.sfedu.teamselection.dto.track.TrackDto;
import ru.sfedu.teamselection.mapper.track.TrackDtoMapper;
import ru.sfedu.teamselection.service.TrackService;
import ru.sfedu.teamselection.service.UserService;

/**
 * Передача состава в кабинет ПД вручную (#15) — если импорт в core прошёл в обход обычного вызова,
 * и отмена ошибочной передачи. Обычно передачу отмечает сам core через интеграционный API.
 *
 * <p>Отдельный контроллер, а не TrackController: тот реализует сгенерированный TrackApi и из-за
 * {@code @PreAuthorize} проксируется по интерфейсу, поэтому метод вне TrackApi молча не получает маршрут.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class TrackHandOverController {
    public static final String HAND_OVER = "/api/v1/tracks/{trackId}/handover";
    public static final String CANCEL_HAND_OVER = "/api/v1/tracks/{trackId}/handover/cancel";

    private final TrackService trackService;
    private final TrackDtoMapper trackDtoMapper;
    private final UserService userService;

    @Operation(
            method = "POST",
            summary = "Отметить состав набора переданным в кабинет ПД",
            description = "После этого набор только для чтения для всех. Повторный вызов ничего не меняет.",
            parameters = {@Parameter(name = "trackId", description = "Id набора", in = ParameterIn.PATH)}
    )
    @PreAuthorize(Access.ADMIN)
    @PostMapping(value = HAND_OVER, produces = MediaType.APPLICATION_JSON_VALUE)
    public TrackDto handOver(@PathVariable Long trackId) {
        log.info("ENTER handOver() endpoint, trackId={}", trackId);
        return trackDtoMapper.mapToDtoWithoutTeams(trackService.handOver(trackId, userService.getCurrentUser()));
    }

    @Operation(
            method = "POST",
            summary = "Отменить передачу состава",
            description = "Для ошибочной отметки. Расхождение с кабинетом ПД остаётся на совести администратора.",
            parameters = {@Parameter(name = "trackId", description = "Id набора", in = ParameterIn.PATH)}
    )
    @PreAuthorize(Access.ADMIN)
    @PostMapping(value = CANCEL_HAND_OVER, produces = MediaType.APPLICATION_JSON_VALUE)
    public TrackDto cancelHandOver(@PathVariable Long trackId) {
        log.info("ENTER cancelHandOver() endpoint, trackId={}", trackId);
        return trackDtoMapper.mapToDtoWithoutTeams(trackService.cancelHandOver(trackId, userService.getCurrentUser()));
    }
}
