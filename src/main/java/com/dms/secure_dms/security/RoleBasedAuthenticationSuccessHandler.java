package com.dms.secure_dms.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RoleBasedAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        String redirectUrl = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(this::mapAuthorityToPath)
                .orElse("/login");

        response.sendRedirect(redirectUrl);
    }

    private String mapAuthorityToPath(String authority) {
        return switch (authority) {
            case "ROLE_ADMIN" -> "/admin";
            case "ROLE_IO" -> "/io";
            case "ROLE_REVIEWER" -> "/reviewer";
            case "ROLE_AUDITOR" -> "/auditor";
            default -> "/login";
        };
    }
}
