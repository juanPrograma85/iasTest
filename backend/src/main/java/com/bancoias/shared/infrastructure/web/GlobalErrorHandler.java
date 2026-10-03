package com.bancoias.shared.infrastructure.web;

import com.bancoias.shared.domain.BusinessRuleViolationException;
import com.bancoias.shared.domain.ConflictException;
import com.bancoias.shared.domain.DomainException;
import com.bancoias.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
public class GlobalErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

    private final Clock clock;

    public GlobalErrorHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleViolationException ex) {
        return domainError(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return domainError(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return domainError(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    ResponseEntity<ErrorResponse> handleBinding(WebExchangeBindException ex) {
        String message = ex.getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Uno o más parámetros de la solicitud son inválidos.");
    }

    @ExceptionHandler(ServerWebInputException.class)
    ResponseEntity<ErrorResponse> handleInput(ServerWebInputException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "El cuerpo o los parámetros de la solicitud tienen un formato inválido.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> handleStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return error(status, status.name(), status.getReasonPhrase());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Ocurrió un error inesperado. Intente nuevamente.");
    }

    private ResponseEntity<ErrorResponse> domainError(HttpStatus status, DomainException ex) {
        return error(status, ex.code(), ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, Instant.now(clock)));
    }
}
