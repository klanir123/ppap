package com.msg.ppap.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Decides which requests are client-side routes of the single page application and therefore have to be answered
 * with {@code index.html}: GET requests outside the backend namespaces whose last path segment contains no dot.
 */
public final class SpaRoutes {

    /** Path prefixes owned by the backend; never treated as SPA routes. */
    static final List<String> BACKEND_PREFIXES =
            List.of("/api", "/actuator", "/v3/api-docs", "/swagger-ui", "/internal", "/error");

    private SpaRoutes() {}

    /** Returns whether the given request targets a client-side SPA route. */
    public static boolean isSpaRoute(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        return isSpaPath(pathWithinApplication(request));
    }

    /** Returns whether the given application-relative path is a client-side SPA route. */
    public static boolean isSpaPath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return false;
        }
        for (String prefix : BACKEND_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return false;
            }
        }
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        return !lastSegment.contains(".");
    }

    /** Spring Security matcher for SPA routes. */
    public static RequestMatcher matcher() {
        return SpaRoutes::isSpaRoute;
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;
    }
}
