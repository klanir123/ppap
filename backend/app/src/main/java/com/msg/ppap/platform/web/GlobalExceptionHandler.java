package com.msg.ppap.platform.web;

import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.platform.domain.PpapException;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.VersionConflictException;
import com.msg.ppap.platform.domain.ViolationCodes;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates exceptions into RFC 9457 {@link ProblemDetail} responses.
 *
 * <p>Every problem carries the request's {@code correlationId} and a stable {@code code} (e.g.
 * {@code VALIDATION_FAILED}, {@code DUPLICATE_KEY}, {@code NOT_FOUND}, {@code VERSION_CONFLICT}, {@code REFERENCED})
 * that the frontend maps to i18n keys; validation problems add {@code errors: [{field, code, message}]}. Internal
 * error details are never exposed to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Problem property holding a stable, machine-readable error code. */
    public static final String CODE_PROPERTY = "code";

    /** Problem property holding field violations. */
    public static final String ERRORS_PROPERTY = "errors";

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Expected business exceptions. */
    @ExceptionHandler(PpapException.class)
    ProblemDetail handleBusiness(PpapException ex) {
        HttpStatus status = statusOf(ex.kind());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setProperty(CODE_PROPERTY, ex.code());
        if (ex instanceof ValidationException validation) {
            problem.setDetail("Request validation failed.");
            problem.setProperty(ERRORS_PROPERTY, errors(validation.violations()));
        }
        return enrich(problem);
    }

    /** JPA optimistic locking (concurrent modification between read and write). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED, "Object was modified concurrently.");
        problem.setProperty(CODE_PROPERTY, VersionConflictException.CODE);
        return enrich(problem);
    }

    /** Unique constraint races that slipped past the application-level checks. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        LOG.info("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The change conflicts with existing data (duplicate key or reference).");
        problem.setProperty(CODE_PROPERTY, ViolationCodes.DUPLICATE_KEY);
        return enrich(problem);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ex.getBody();
        problem.setProperty(CODE_PROPERTY, ValidationException.CODE);
        problem.setProperty(
                ERRORS_PROPERTY,
                ex.getBindingResult().getFieldErrors().stream()
                        .map(error -> Map.of(
                                "field", error.getField(),
                                "code", ViolationCodes.INVALID_VALUE,
                                "message", String.valueOf(error.getDefaultMessage())))
                        .toList());
        return createResponseEntity(problem, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Malformed request body.");
        problem.setProperty(CODE_PROPERTY, "MALFORMED_REQUEST");
        return createResponseEntity(problem, headers, status, request);
    }

    /** Bean validation failures on method parameters (e.g. path variables). */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed.");
        problem.setProperty(CODE_PROPERTY, ValidationException.CODE);
        problem.setProperty(
                ERRORS_PROPERTY,
                ex.getConstraintViolations().stream()
                        .map(v -> Map.of(
                                "field", v.getPropertyPath().toString(),
                                "code", ViolationCodes.INVALID_VALUE,
                                "message", v.getMessage()))
                        .toList());
        return enrich(problem);
    }

    /** Method security ({@code @PreAuthorize}) denials. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied.");
        problem.setProperty(CODE_PROPERTY, "FORBIDDEN");
        return enrich(problem);
    }

    /** Authentication failures raised below the security filter chain. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication required.");
        problem.setProperty(CODE_PROPERTY, "UNAUTHORIZED");
        return enrich(problem);
    }

    /** Last resort: log with correlation id, return a generic 500 without internals. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        LOG.error("Unhandled exception", ex);
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        problem.setProperty(CODE_PROPERTY, "INTERNAL_ERROR");
        return enrich(problem);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            enrich(problem);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    /** HTTP status for a business exception kind. */
    static HttpStatus statusOf(PpapException.Kind kind) {
        return switch (kind) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case UNSUPPORTED -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        };
    }

    private static List<Map<String, String>> errors(List<FieldViolation> violations) {
        return violations.stream()
                .map(v -> Map.of("field", v.field(), "code", v.code(), "message", v.message()))
                .toList();
    }

    private static ProblemDetail enrich(ProblemDetail problem) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty(CorrelationIdFilter.MDC_KEY, correlationId);
        }
        if (problem.getProperties() == null || !problem.getProperties().containsKey(CODE_PROPERTY)) {
            problem.setProperty(CODE_PROPERTY, defaultCode(problem.getStatus()));
        }
        return problem;
    }

    private static String defaultCode(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 412 -> "VERSION_CONFLICT";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "HTTP_" + status;
        };
    }
}
