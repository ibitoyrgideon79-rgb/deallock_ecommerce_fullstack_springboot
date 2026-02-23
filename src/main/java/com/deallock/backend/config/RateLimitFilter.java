package com.deallock.backend.config;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000L;

    private static class Counter {
        long windowStartMs;
        int count;

        Counter(long windowStartMs) {
            this.windowStartMs = windowStartMs;
            this.count = 0;
        }
    }

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (!"POST".equalsIgnoreCase(method)) {
            return true;
        }

        return !(path.equals("/api/send-otp")
                || path.equals("/api/verify-otp")
                || path.equals("/api/signup")
                || path.equals("/login")
                || path.equals("/forgot-password"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String clientIp = getClientIp(request);
        String key = clientIp + ":" + path;

        int limit = limitForPath(path);
        long now = System.currentTimeMillis();

        Counter counter = counters.computeIfAbsent(key, k -> new Counter(now));
        synchronized (counter) {
            if (now - counter.windowStartMs >= WINDOW_MS) {
                counter.windowStartMs = now;
                counter.count = 0;
            }

            if (counter.count >= limit) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"message\":\"Too many requests. Please try again later.\"}");
                return;
            }

            counter.count++;
        }

        filterChain.doFilter(request, response);
    }

    private int limitForPath(String path) {
        if (path.equals("/api/send-otp")) {
            return 5;
        }
        if (path.equals("/api/verify-otp")) {
            return 10;
        }
        if (path.equals("/api/signup")) {
            return 5;
        }
        if (path.equals("/login")) {
            return 10;
        }
        if (path.equals("/forgot-password")) {
            return 5;
        }
        return 10;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
