package com.siddu.auth.config;

import com.siddu.commonsecurity.Filter.GatewayAuthenticationFilter;
import com.siddu.commonsecurity.Filter.JwtAuthenticationFilter;
import com.siddu.commonsecurity.exception.JwtAccessDeniedHandler;
import com.siddu.commonsecurity.exception.JwtAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    @RequiredArgsConstructor
    public class SecurityConfig {
        private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
        private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
        private final GatewayAuthenticationFilter gatewayAuthenticationFilter;



        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

            http
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(session ->
                            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    )
                    .exceptionHandling(ex -> ex
                            .accessDeniedHandler(jwtAccessDeniedHandler)
                            .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                    )
                    .authorizeHttpRequests(auth -> auth
                            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                            .requestMatchers(
                                    "/Auth/register",
                                    "/Auth/login",
                                    "/Auth/refresh",
                                    "/Auth/logout",
                                    "/internal/**",
                                    "/Auth/send-otp",
                                    "/Auth/verify-otp"
                            ).permitAll()
                            .requestMatchers("/error").permitAll()
                            .requestMatchers("/admin/**").hasAnyRole("ADMIN","MANAGER")
                            .anyRequest().authenticated()
                    )
                    .addFilterBefore(
                            gatewayAuthenticationFilter,
                            UsernamePasswordAuthenticationFilter.class
                    );

            return http.build();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(12);
        }
    }
