package com.msg.ppap.platform.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.method.HandlerMethod;

/**
 * Documents the conventional responses of all API operations so that the OpenAPI contract matches the actual
 * behaviour: {@code 201}/{@code Location} for creates ({@link CreatedResponse}, {@link UpsertResponse}),
 * {@code 204} for deletes and RFC 9457 problem responses ({@code application/problem+json},
 * schema {@value OpenApiConfiguration#PROBLEM_SCHEMA}) derived from the handler signature:
 *
 * <ul>
 *   <li>401 for every non-public operation, 403 for operations with {@code @PreAuthorize}
 *   <li>400 for request bodies, multipart parts, query and path parameters
 *   <li>404 for path variables, 412 for {@code If-Match}, 409 for JSON writes (duplicate key / referenced)
 *   <li>413 and 415 for file uploads
 * </ul>
 *
 * Explicitly documented responses (e.g. 422 with an import report) are never overwritten.
 */
@Component
public class ApiResponsesCustomizer implements OperationCustomizer {

    static final String PROBLEM_JSON = "application/problem+json";

    @Override
    public Operation customize(Operation operation, HandlerMethod handler) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        ApiResponse ok = responses.get("200");

        if (handler.hasMethodAnnotation(DeleteMapping.class)) {
            responses.remove("200");
            responses.addApiResponse("204", new ApiResponse().description("Deleted, or deactivated if referenced"));
        }
        if (handler.hasMethodAnnotation(CreatedResponse.class)) {
            responses.remove("200");
            responses.addApiResponse("201", created(ok));
        }
        if (handler.hasMethodAnnotation(UpsertResponse.class)) {
            if (ok != null) {
                ok.setDescription("Updated or unchanged");
            }
            responses.addApiResponse("201", created(ok));
        }

        MethodParameter[] parameters = handler.getMethodParameters();
        boolean body = has(parameters, RequestBody.class);
        boolean multipart = has(parameters, RequestPart.class);
        boolean params = has(parameters, RequestParam.class);
        boolean pathVariables = has(parameters, PathVariable.class);
        boolean ifMatch = Arrays.stream(parameters)
                .map(p -> p.getParameterAnnotation(RequestHeader.class))
                .anyMatch(h -> h != null && HttpHeaders.IF_MATCH.equalsIgnoreCase(h.name()));
        boolean write = handler.hasMethodAnnotation(PostMapping.class) || handler.hasMethodAnnotation(PutMapping.class);

        if (body || multipart || params || pathVariables) {
            problem(responses, "400", "Invalid request (code VALIDATION_FAILED with errors, MALFORMED_REQUEST, …)");
        }
        if (!handler.hasMethodAnnotation(SecurityRequirements.class)) {
            problem(responses, "401", "Not authenticated");
        }
        if (handler.hasMethodAnnotation(PreAuthorize.class)
                || handler.getBeanType().isAnnotationPresent(PreAuthorize.class)) {
            problem(responses, "403", "Missing role (code FORBIDDEN)");
        }
        if (pathVariables) {
            problem(responses, "404", "Not found (code NOT_FOUND)");
        }
        if (write && body) {
            problem(responses, "409", "Conflict (code DUPLICATE_KEY or REFERENCED)");
        }
        if (ifMatch) {
            problem(responses, "412", "If-Match does not match the current version (code VERSION_CONFLICT)");
        }
        if (multipart) {
            problem(responses, "413", "File too large (code FILE_TOO_LARGE)");
            problem(responses, "415", "Unsupported file type (code UNSUPPORTED_FILE_TYPE)");
        }
        return operation;
    }

    private static ApiResponse created(ApiResponse ok) {
        var created = new ApiResponse()
                .description("Created")
                .addHeaderObject(
                        HttpHeaders.LOCATION,
                        new Header().description("URL of the created resource").schema(new StringSchema()));
        if (ok != null) {
            created.setContent(ok.getContent());
            if (ok.getHeaders() != null) {
                ok.getHeaders().forEach(created::addHeaderObject);
            }
        }
        return created;
    }

    private static void problem(ApiResponses responses, String status, String description) {
        if (responses.containsKey(status)) {
            return;
        }
        responses.addApiResponse(
                status,
                new ApiResponse()
                        .description(description)
                        .content(new Content()
                                .addMediaType(
                                        PROBLEM_JSON,
                                        new MediaType()
                                                .schema(new Schema<>()
                                                        .$ref("#/components/schemas/"
                                                                + OpenApiConfiguration.PROBLEM_SCHEMA)))));
    }

    private static boolean has(MethodParameter[] parameters, Class<? extends Annotation> annotation) {
        return Arrays.stream(parameters).anyMatch(p -> p.hasParameterAnnotation(annotation));
    }
}
