/*******************************************************************************
 * Copyright (c) 2015, 2025, 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.ui.servlet.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;

import java.util.UUID;
import javax.servlet.http.Cookie;
/**
 *
 */
public class SessionFilter implements Filter {

    private FilterConfig filterConfig;
    private static final TraceComponent tc = Tr.register(SessionFilter.class);
    private static final String LOGIN_ERROR_PAGE = "/adminCenter/login.jsp?no_access";
    private static final String COOKIE_NAME = "csrfToken";
    private static final String CSRF_HEADER_NAME = "X-CSRF-Token";
    private static final String SET_COOKIE_HEADER = "Set-Cookie";
    private static final int TOKEN_MAX_AGE = 3600; // 1 hour
    private static final String CSRF_VALIDATION_ERROR_MSG = "CSRF token validation failed";

    /**
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
    }

    /**
     * @see javax.servlet.Filter#destroy()
     */
    @Override
    public void destroy() {
        this.filterConfig = null;
    }

    /**
     * Retrieves CSRF token from cookie
     */
    public String getCsrfTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Validates CSRF token format (UUID)
     */
    private boolean isValidTokenFormat(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        // UUID format: 8-4-4-4-12 hexadecimal characters
        return token.matches("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$");
    }

    /**
     * Validates CSRF token using double-submit cookie pattern
     * Token must match in both cookie and request header
     */
    private boolean validateCsrfToken(HttpServletRequest request) {
        String cookieToken = getCsrfTokenFromCookie(request);
        String headerToken = request.getHeader(CSRF_HEADER_NAME);

        if (tc.isDebugEnabled()) {
            Tr.debug(tc, "CSRF Validation - Cookie Token: " + (cookieToken != null ? "present" : "null") +
                     ", Header Token: " + (headerToken != null ? "present" : "null"));
        }

        // Both tokens must be present and match
        if (cookieToken == null || headerToken == null) {
            return false;
        }

        // Validate format
        if (!isValidTokenFormat(cookieToken) || !isValidTokenFormat(headerToken)) {
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "CSRF token format validation failed");
            }
            return false;
        }

        // Tokens must match (constant-time comparison to prevent timing attacks)
        return constantTimeEquals(cookieToken, headerToken);
    }

    /**
     * Constant-time string comparison to prevent timing attacks
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * Checks if the request requires CSRF validation
     */
    private boolean requiresCsrfValidation(String method, String uri) {
        // Validate state-changing methods
        if ("POST".equals(method) || "PUT".equals(method) ||
            "DELETE".equals(method) || "PATCH".equals(method)) {
            
            // Exclude login endpoint - needs token generation, not validation
            if (uri.endsWith("/j_security_check")) {
                return false;
            }
            
            // Exclude logout endpoint - user is logging out anyway
            if (uri.endsWith("/ibm_security_logout")) {
                return false;
            }
            
            // Exclude static resources and login page resources
            // Check if URI contains these paths (works for any context root)
            if (uri.contains("/dojo/") ||
                uri.contains("/login/") ||
                uri.contains("/fonts/") ||
                uri.contains("/404/")) {
                return false;
            }
            
            // Validate all other state-changing requests
            return true;
        }
        return false;
    }

    /**
     * Builds the Set-Cookie header value with security attributes
     */
    private String buildCookieHeaderValue(String token, boolean isSecure) {
        return String.format(
            "%s=%s; Path=/; Max-Age=%d; SameSite=Strict%s",
            COOKIE_NAME,
            token,
            TOKEN_MAX_AGE,
            isSecure ? "; Secure" : ""
        );
    }

    /**
     * Generates and sets a new CSRF token if one doesn't exist.
     * Reuses existing valid tokens to maintain consistency across requests.
     * Only sets the cookie when a new token is generated to reduce overhead.
     */
    private void generateAndSetCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        String token = getCsrfTokenFromCookie(request);
        if (token == null) {
            // Generate new token only if completely missing
            token = UUID.randomUUID().toString();
            
            // Set cookie with all security attributes including SameSite
            // Using addHeader() because Cookie class doesn't support SameSite attribute
            String cookieValue = buildCookieHeaderValue(token, request.isSecure());
            response.addHeader(SET_COOKIE_HEADER, cookieValue);
            
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Generated new CSRF token");
            }
        } else if (!isValidTokenFormat(token)) {
            // Token exists but has invalid format - log warning but don't regenerate
            // This prevents race conditions in multi-tab scenarios
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Invalid CSRF token format detected - will fail validation");
            }
        }
        // If token exists and is valid, do nothing - reuse existing token
        // This reduces overhead by not setting the cookie on every request
    }

    /**
     * Validates security check endpoint to prevent GET-based authentication
     *
     * @return true if request should be blocked, false otherwise
     */
    private boolean handleSecurityCheckValidation(HttpServletRequest request, HttpServletResponse response, String requestURI) throws IOException {
        HttpSession session = request.getSession(false);

        if (tc.isDebugEnabled()) {
            Tr.debug(tc, "doFilter", requestURI);
        }

        // We can't allow users to authenticate by navigating directly to a URL like
        // https://localhost:9443/adminCenter/j_security_check?j_username=admin&j_password=adminpwd
        // Doing so creates a security vulnerability
        if (requestURI.endsWith("/j_security_check") && request.getMethod().equals("GET")) {
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Session = " + session);
                Tr.debug(tc, "Redirecting to " + LOGIN_ERROR_PAGE);
            }
            response.sendRedirect(LOGIN_ERROR_PAGE);
            if (session != null) {
                session.invalidate();
            }
            return true;
        }
        return false;
    }

    /**
     * Handles CSRF validation failure by logging and sending error response
     */
    private void handleCsrfValidationFailure(HttpServletResponse response, String method, String uri) throws IOException {
        if (tc.isDebugEnabled()) {
            Tr.debug(tc, "CSRF validation failed for " + method + " " + uri);
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN, CSRF_VALIDATION_ERROR_MSG);
    }

    /**
     * Filters requests and validates CSRF tokens for state-changing operations.
     *
     * <p>Security Features:</p>
     * <ul>
     *   <li>Generates CSRF tokens using Double-Submit Cookie Pattern</li>
     *   <li>Validates tokens for POST, PUT, DELETE, PATCH requests</li>
     *   <li>Prevents GET-based authentication on j_security_check</li>
     *   <li>Uses constant-time comparison to prevent timing attacks</li>
     * </ul>
     *
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {

        if (tc.isEntryEnabled()) {
            Tr.entry(tc, "doFilter");
        }
        
        HttpServletRequest httpRequest = (HttpServletRequest) req;
        HttpServletResponse httpResponse = (HttpServletResponse) resp;
        String requestURI = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        // Generate or retrieve CSRF token
        generateAndSetCsrfToken(httpRequest, httpResponse);

        // Validate CSRF token for state-changing requests (Double-Submit Cookie Pattern)
        if (requiresCsrfValidation(method, requestURI)) {
            if (!validateCsrfToken(httpRequest)) {
                handleCsrfValidationFailure(httpResponse, method, requestURI);
                return;
            }
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "CSRF validation passed for " + method + " " + requestURI);
            }
        }

        // Additional security check for j_security_check endpoint
        if (handleSecurityCheckValidation(httpRequest, httpResponse, requestURI)) {
            return;
        }

        try {
            chain.doFilter(req, resp);
        } catch (ServletException e) {
            // Check if this is a FileNotFoundException for a missing resource
            // This can happen during testing or when accessing non-existent endpoints
            Throwable cause = e.getCause();
            if (cause instanceof java.io.FileNotFoundException) {
                // Log at debug level to avoid FFDC noise for expected 404s
                if (tc.isDebugEnabled()) {
                    Tr.debug(tc, "File not found: " + requestURI + " - " + cause.getMessage());
                }
                // Let the container handle the 404 response
                if (!httpResponse.isCommitted()) {
                    httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
                }
                return;
            }
            // Re-throw other ServletExceptions
            throw e;
        }

        if (tc.isEntryEnabled()) {
            Tr.exit(tc, "doFilter");
        }
    }
}
