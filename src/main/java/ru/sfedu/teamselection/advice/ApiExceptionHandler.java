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
import ru.sfedu.teamselection.dto.ErrorResponse;
import ru.sfedu.teamselection.exception.BusinessException;
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

    @ExceptionHandler(value = {
            IllegalArgumentException.class,
            ConstraintDeclarationException.class,
            BusinessException.class
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleServerError(
            Exception ex, HttpServletRequest req
    ) {
        log.error(ex.getMessage());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex, req);
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
