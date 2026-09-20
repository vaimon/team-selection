package ru.sfedu.teamselection.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.sfedu.teamselection.config.security.Access;
import ru.sfedu.teamselection.dto.PageResponse;
import ru.sfedu.teamselection.dto.activity.ActivityEntryDto;
import ru.sfedu.teamselection.mapper.PageResponseMapper;
import ru.sfedu.teamselection.service.ActivityService;

/**
 * История действий над составом (#16). Только для администратора: записи называют студентов
 * по имени, а это те же данные, что и в анкетах.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ActivityController {
    public static final String HISTORY = "/api/v1/admin/activity";
    public static final String PURGE = "/api/v1/admin/activity/purge";

    private static final String DEFAULT_PAGE_SIZE = "50";

    private final ActivityService activityService;
    private final PageResponseMapper pageResponseMapper;

    @Operation(
            method = "GET",
            summary = "История действий над составом",
            description = "Свежие записи сверху. Незаданный фильтр ничего не сужает.",
            parameters = {
                    @Parameter(name = "teamId", description = "Команда; для перемещений — и та, и другая",
                            in = ParameterIn.QUERY),
                    @Parameter(name = "studentId", description = "Студент, которого касается запись",
                            in = ParameterIn.QUERY),
                    @Parameter(name = "actorUserId", description = "Кто совершил действие", in = ParameterIn.QUERY),
                    @Parameter(name = "from", description = "С какого момента, ISO", in = ParameterIn.QUERY),
                    @Parameter(name = "to", description = "По какой момент, ISO", in = ParameterIn.QUERY)
            }
    )
    @PreAuthorize(Access.ADMIN)
    @GetMapping(value = HISTORY, produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<ActivityEntryDto> history(
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size
    ) {
        log.info("ENTER history() endpoint, teamId={}, studentId={}, actorUserId={}", teamId, studentId, actorUserId);
        return pageResponseMapper.toDto(
                activityService.history(teamId, studentId, actorUserId, from, to, PageRequest.of(page, size)));
    }

    @Operation(
            method = "POST",
            summary = "Удалить записи старше срока хранения",
            description = """
                Срок задаётся настройкой app.activity.retention-days. Та же чистка идёт сама,
                когда администратор начинает новый набор.
                """
    )
    @PreAuthorize(Access.ADMIN)
    @PostMapping(value = PURGE, produces = MediaType.APPLICATION_JSON_VALUE)
    public long purge() {
        log.info("ENTER purge() endpoint");
        return activityService.purge();
    }
}
