package ru.sfedu.teamselection.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.sfedu.teamselection.config.logging.Auditable;
import ru.sfedu.teamselection.config.security.Access;
import ru.sfedu.teamselection.dto.board.BoardLeadRequest;
import ru.sfedu.teamselection.dto.board.BoardMoveRequest;
import ru.sfedu.teamselection.dto.board.BoardTargetsRequest;
import ru.sfedu.teamselection.dto.board.BoardVersionRequest;
import ru.sfedu.teamselection.dto.board.CompositionBoardDto;
import ru.sfedu.teamselection.service.CompositionBoardService;
import ru.sfedu.teamselection.service.UserService;

/**
 * Доска состава (#14). Каждое изменение возвращает доску целиком — со свежими версиями команд для
 * следующего действия. Отказы по состоянию — 409 с полем code (ConflictReason).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CompositionBoardController {
    public static final String BOARD = "/api/v1/admin/board";
    public static final String MOVES = "/api/v1/admin/board/moves";
    public static final String TARGETS = "/api/v1/admin/board/teams/{teamId}/targets";
    public static final String LEAD = "/api/v1/admin/board/teams/{teamId}/lead";
    public static final String DISSOLVE = "/api/v1/admin/board/teams/{teamId}/dissolve";

    private final CompositionBoardService boardService;
    private final UserService userService;

    @Operation(
            method = "GET",
            summary = "Доска состава текущего набора",
            description = "Все команды с участниками, счётчиками по курсам и целями, и пул студентов без команды."
    )
    @PreAuthorize(Access.ADMIN)
    @GetMapping(value = BOARD, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompositionBoardDto board() {
        log.info("ENTER board() endpoint");
        return boardService.board();
    }

    @Operation(
            method = "POST",
            summary = "Переместить студента",
            description = """
                Из команды или пула в другую команду или в пул. Превышение цели — только с
                allowOverTarget, тимлид перемещается только с преемником.
                """
    )
    @PreAuthorize(Access.ADMIN)
    @PostMapping(value = MOVES, produces = MediaType.APPLICATION_JSON_VALUE)
    @Auditable(auditPoint = "Board.Move")
    public CompositionBoardDto move(@Valid @RequestBody BoardMoveRequest request) {
        log.info("ENTER move() endpoint");
        return boardService.move(request, userService.getCurrentUser());
    }

    @Operation(
            method = "PUT",
            summary = "Цели команды по курсам",
            description = "null по курсу снимает переопределение: действует цель набора.",
            parameters = {@Parameter(name = "teamId", description = "Id команды", in = ParameterIn.PATH)}
    )
    @PreAuthorize(Access.ADMIN)
    @PutMapping(value = TARGETS, produces = MediaType.APPLICATION_JSON_VALUE)
    @Auditable(auditPoint = "Board.SetTargets")
    public CompositionBoardDto setTargets(@PathVariable Long teamId, @Valid @RequestBody BoardTargetsRequest request) {
        log.info("ENTER setTargets() endpoint, teamId={}", teamId);
        return boardService.setTargets(teamId, request);
    }

    @Operation(
            method = "POST",
            summary = "Сменить тимлида команды",
            parameters = {@Parameter(name = "teamId", description = "Id команды", in = ParameterIn.PATH)}
    )
    @PreAuthorize(Access.ADMIN)
    @PostMapping(value = LEAD, produces = MediaType.APPLICATION_JSON_VALUE)
    @Auditable(auditPoint = "Board.ChangeLead")
    public CompositionBoardDto changeLead(@PathVariable Long teamId, @Valid @RequestBody BoardLeadRequest request) {
        log.info("ENTER changeLead() endpoint, teamId={}", teamId);
        return boardService.changeLead(teamId, request, userService.getCurrentUser());
    }

    @Operation(
            method = "POST",
            summary = "Распустить команду",
            description = "Участники уходят в пул, заявки команды удаляются вместе с ней.",
            parameters = {@Parameter(name = "teamId", description = "Id команды", in = ParameterIn.PATH)}
    )
    @PreAuthorize(Access.ADMIN)
    @PostMapping(value = DISSOLVE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Auditable(auditPoint = "Board.Dissolve")
    public CompositionBoardDto dissolve(@PathVariable Long teamId, @Valid @RequestBody BoardVersionRequest request) {
        log.info("ENTER dissolve() endpoint, teamId={}", teamId);
        return boardService.dissolve(teamId, request, userService.getCurrentUser());
    }
}
