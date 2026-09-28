package com.chris64233.cc.wastemanifest.manifest.exception;

import com.chris64233.cc.wastemanifest.correction.exception.CorrectionNotFoundException;
import com.chris64233.cc.wastemanifest.incident.exception.IncidentNotFoundException;
import com.chris64233.cc.wastemanifest.incident.exception.SegmentNotFoundException;
import com.chris64233.cc.wastemanifest.manifest.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ManifestNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(ManifestNotFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(CorrectionNotFoundException.class)
    public ResponseEntity<ErrorResponse> correctionNotFound(CorrectionNotFoundException e,
                                                            HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(IncidentNotFoundException.class)
    public ResponseEntity<ErrorResponse> incidentNotFound(IncidentNotFoundException e,
                                                          HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(SegmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> segmentNotFound(SegmentNotFoundException e,
                                                         HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(ConflictException e, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> businessRule(BusinessRuleException e, HttpServletRequest request) {
        return build(HttpStatusCode.valueOf(422), e.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> dataIntegrity(DataIntegrityViolationException e, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "数据唯一性约束冲突", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatusCode status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(),
                HttpStatus.valueOf(status.value()).getReasonPhrase(), message, request.getRequestURI()));
    }
}
