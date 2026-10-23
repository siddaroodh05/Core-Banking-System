package com.siddu.commonsecurity.Filter;

import com.siddu.commonsecurity.Jwt.JwtValidator;
import com.siddu.commonsecurity.Jwt.CheckTokenBlockList;
import com.siddu.commonsecurity.exception.JwtAuthenticationEntryPoint;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

   private final JwtValidator jwtValidator;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final CheckTokenBlockList checktokenBlockList;

    public JwtAuthenticationFilter(JwtValidator jwtValidator,
                                   JwtAuthenticationEntryPoint authenticationEntryPoint,
                                   CheckTokenBlockList checktokenBlockList) {
        this.jwtValidator = jwtValidator;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.checktokenBlockList = checktokenBlockList;

    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getServletPath();

        return path.equals("/Auth/login")
                || path.equals("/Auth/register")
                || path.equals("/Auth/refresh")
                || path.equals("/Auth/logout")
                || path.equals("/Auth/send-otp")
                || path.equals("/Auth/verify-otp");

    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {


        Cookie[] cookies = request.getCookies();

        String token = null;

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("ACCESS_TOKEN".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (!jwtValidator.isTokenValid(token)) {
                authenticationEntryPoint.commence(
                        request,
                        response,
                        new InsufficientAuthenticationException("Invalid or expired token")
                );
                return;
            }

            UUID userId = jwtValidator.extractUserId(token);

            if(checktokenBlockList.isUserLoggedOut(userId)){
                authenticationEntryPoint.commence(
                        request,
                        response,
                        new InsufficientAuthenticationException("Invalid or expired token,please login again")
                );
                return;

            }

            List<String> roles = jwtValidator.extractRoles(token);


            List<GrantedAuthority> authorities =
                    roles.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            authorities
                    );


            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);


        }catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();

            authenticationEntryPoint.commence(
                    request,
                    response,
                    new InsufficientAuthenticationException(
                            "Invalid or expired token", ex)
            );

            return;
        }

        filterChain.doFilter(request, response);
    }
}