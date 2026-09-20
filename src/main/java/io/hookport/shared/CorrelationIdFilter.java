package io.hookport.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class CorrelationIdFilter
        extends OncePerRequestFilter {

    public static final String HEADER =
            "X-Correlation-Id";

    private static final Pattern VALID_ID = Pattern.compile(
            "[A-Za-z0-9._-]{1,100}"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(
                request.getHeader(HEADER)
        );

        response.setHeader(HEADER, correlationId);

        try (MDC.MDCCloseable ignored =
                     MDC.putCloseable(
                             "correlationId",
                             correlationId
                     )) {
            filterChain.doFilter(request, response);
        }
    }

    private String resolveCorrelationId(String supplied) {
        if (supplied != null &&
                VALID_ID.matcher(supplied).matches()) {
            return supplied;
        }

        return UUID.randomUUID().toString();
    }
}