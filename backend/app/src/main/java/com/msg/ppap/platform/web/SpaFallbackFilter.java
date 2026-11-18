package com.msg.ppap.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Forwards client-side routes of the bundled React SPA (see {@link SpaRoutes}) to {@code /index.html}, so deep links
 * and browser reloads work. Runs after the Spring Security filter chain.
 */
@Component
public class SpaFallbackFilter extends OncePerRequestFilter {

    static final String INDEX = "/index.html";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SpaRoutes.isSpaRoute(request)) {
            request.getRequestDispatcher(INDEX).forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }
}
