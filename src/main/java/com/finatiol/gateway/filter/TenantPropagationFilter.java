package com.finatiol.gateway.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Order(0)
public class TenantPropagationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantPropagationFilter.class);
    private static final String TENANT_HEADER = "X-Tenant-ID";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String token = extractBearerToken(request);
        if (token != null) {
            String tenantId = extractTenantId(token);
            if (tenantId != null && !tenantId.isBlank()) {
                log.debug("[TenantPropagation] tenantId={} para request {}", tenantId, request.getRequestURI());
                chain.doFilter(new TenantHeaderRequestWrapper(request, tenantId), response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private String extractTenantId(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1] + "==");
            Map<String, Object> claims = objectMapper.readValue(decoded, new TypeReference<>() {});
            Object tenantId = claims.get("tenantId");
            return tenantId != null ? tenantId.toString() : null;
        } catch (Exception e) {
            log.debug("[TenantPropagation] No se pudo extraer tenantId del token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Wrapper que inyecta el header X-Tenant-ID en la request.
     * Necesario porque HttpServletRequest no permite agregar headers mutables.
     */
    static class TenantHeaderRequestWrapper extends HttpServletRequestWrapper {

        private final String tenantId;

        TenantHeaderRequestWrapper(HttpServletRequest request, String tenantId) {
            super(request);
            this.tenantId = tenantId;
        }

        @Override
        public String getHeader(String name) {
            if (TENANT_HEADER.equalsIgnoreCase(name)) return tenantId;
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (TENANT_HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(tenantId));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
            if (!names.stream().anyMatch(TENANT_HEADER::equalsIgnoreCase)) {
                names.add(TENANT_HEADER);
            }
            return Collections.enumeration(names);
        }
    }
}
