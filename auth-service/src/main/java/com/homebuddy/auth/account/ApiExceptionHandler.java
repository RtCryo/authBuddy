package com.homebuddy.auth.account;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Maps failures to RFC 9457 problem details without echoing submitted values. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException exception) {
        String detail = exception.getReason();
        if (detail == null || detail.isBlank()) {
            detail = exception.getStatusCode().toString();
        }
        return problem(
                ProblemDetail.forStatusAndDetail(exception.getStatusCode(), detail), exception);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleInvalid(MethodArgumentNotValidException exception) {
        return problem(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request"), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException exception) {
        return problem(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body"),
                null);
    }

    private static ResponseEntity<ProblemDetail> problem(
            ProblemDetail body, ResponseStatusException source) {
        var builder =
                ResponseEntity.status(body.getStatus())
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (source != null) {
            builder.headers(source.getHeaders());
        }
        return builder.body(body);
    }
}
