package com.beat.pay.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 为每个请求生成 requestId，写入 MDC 与响应头，便于日志追踪 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MDC.put(Constants.MDC_REQUEST_ID, id);
        response.setHeader(Constants.HEADER_REQUEST_ID, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(Constants.MDC_REQUEST_ID);
        }
    }
}
