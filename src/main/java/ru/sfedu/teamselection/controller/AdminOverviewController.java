package ru.sfedu.teamselection.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.sfedu.teamselection.config.security.Access;
import ru.sfedu.teamselection.dto.AdminOverviewDto;
import ru.sfedu.teamselection.service.AdminOverviewService;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
public class AdminOverviewController {
    public static final String OVERVIEW = "/api/v1/admin/overview";

    /** Сколько дней заявка должна провисеть, чтобы считаться залежавшейся, если не спросили иначе. */
    private static final String DEFAULT_STALE_DAYS = "3";

    private final AdminOverviewService adminOverviewService;

    @Operation(
            method = "GET",
            summary = "Состояние текущего набора",
            description = """
                Регистрации по курсам, распределение по командам, укомплектованность команд
                и незакрытые заявки. Только для администратора.
                """,
            parameters = {
                    @Parameter(
                            name = "pendingOlderThanDays",
                            description = "Порог, с которого заявка считается залежавшейся",
                            in = ParameterIn.QUERY
                    )
            }
    )
    @PreAuthorize(Access.ADMIN)
    @GetMapping(value = OVERVIEW, produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminOverviewDto overview(
            @RequestParam(defaultValue = DEFAULT_STALE_DAYS) @Min(0) int pendingOlderThanDays
    ) {
        log.info("ENTER overview() endpoint, pendingOlderThanDays={}", pendingOlderThanDays);
        return adminOverviewService.overview(pendingOlderThanDays);
    }
}
