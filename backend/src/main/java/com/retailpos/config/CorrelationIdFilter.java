package com.retailpos.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)
public class CorrelationIdFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    public static final String CORRELATION_ID_HEADER = "X-Request-ID";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            String requestId = httpRequest.getHeader(CORRELATION_ID_HEADER);
            if (requestId == null || requestId.isBlank()) {
                requestId = "REQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }

            MDC.put("requestId", requestId);
            httpResponse.setHeader(CORRELATION_ID_HEADER, requestId);

            // Add Security Headers
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setHeader("X-Frame-Options", "SAMEORIGIN");
            httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
            httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

            long startTime = System.currentTimeMillis();
            try {
                chain.doFilter(request, response);
            } finally {
                long duration = System.currentTimeMillis() - startTime;
                log.info("[HTTP_REQUEST] requestId={} method={} uri={} status={} durationMs={}",
                        requestId, httpRequest.getMethod(), httpRequest.getRequestURI(),
                        httpResponse.getStatus(), duration);
                MDC.remove("requestId");
            }
        } else {
            chain.doFilter(request, response);
        }
    }
}
