package ru.sfedu.teamselection.advice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintDeclarationException;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import ru.sfedu.teamselection.dto.ErrorResponse;
import ru.sfedu.teamselection.exception.BusinessException;
import ru.sfedu.teamselection.exception.ConstraintViolationException;
import ru.sfedu.teamselection.exception.ForbiddenException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoSuchElementException ex, HttpServletRequest req
    ) {
        return buildResponse(HttpStatus.NOT_FOUND, ex, req);
    }

    /**
     * Обращение по несуществующему адресу. Без этого обработчика оно доходило до catch-all ниже и
     * возвращалось как 500 с текстом «No endpoint GET /api/v1/tracks.» — фронту нечего с этим делать.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleUnknownRoute(
            NoHandlerFoundException ex, HttpServletRequest req
    ) {
        return buildResponse(HttpStatus.NOT_FOUND, ex, req);
    }

    @ExceptionHandler(value = {
            IllegalArgumentException.class,
            ConstraintDeclarationException.class,
            BusinessException.class,
            // Негодный query-параметр — тоже про запрос: до #10 в проекте не было ни одной валидации
            // параметров, поэтому эти исключения никто не обрабатывал и они уходили в catch-all как 500.
            HandlerMethodValidationException.class,
            jakarta.validation.ConstraintViolationException.class,
            // Своё исключение бизнес-правил: без явной записи здесь его ловил catch-all и отдавал 500.
            // «Студент уже состоит в команде» и «нет мест для курса» — это про запрос, а не про сервер.
            ConstraintViolationException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(
            RuntimeException ex, HttpServletRequest req
    ) {
        log.error(ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex, req);
    }

    @ExceptionHandler(value = { ForbiddenException.class, AccessDeniedException.class })
    public ResponseEntity<ErrorResponse> handleAccessDeniedRequest(
            RuntimeException ex, HttpServletRequest req
    ) {
        log.error(ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, ex, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest req
    ) {
        log.error(ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex, req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(
            MethodArgumentNotValidException ex, HttpServletRequest req
    ) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.builder()
                .timestamp(OffsetDateTime.now().toString())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(message)
                .path(req.getRequestURI())
                .build());
    }

    /**
     * Всё, что сюда дошло, — наша ошибка, а не клиента: текст исключения идёт в лог целиком,
     * клиенту уходит нейтральное сообщение, чтобы наружу не утекали детали внутренностей.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleServerError(
            Exception ex, HttpServletRequest req
    ) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.builder()
                .timestamp(OffsetDateTime.now().toString())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("Внутренняя ошибка сервера")
                .path(req.getRequestURI())
                .build());
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status, Exception ex, HttpServletRequest req
    ) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now().toString())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(ex.getMessage())
                .path(req.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
