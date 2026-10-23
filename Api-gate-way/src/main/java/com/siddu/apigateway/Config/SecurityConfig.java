package com.siddu.apigateway.Config;

import com.siddu.commonsecurity.Filter.JwtAuthenticationFilter;
import com.siddu.commonsecurity.Jwt.CheckTokenBlockList;
import com.siddu.commonsecurity.Jwt.JwtValidator;
import com.siddu.commonsecurity.exception.JwtAccessDeniedHandler;
import com.siddu.commonsecurity.exception.JwtAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtValidator jwtValidator;
    private final CheckTokenBlockList checkTokenBlockList;







    SecurityConfig(
                   JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                   JwtAccessDeniedHandler  jwtAccessDeniedHandler,
                   JwtValidator jwtValidator,CheckTokenBlockList checkTokenBlockList) {
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
        this.jwtValidator = jwtValidator;
        this.checkTokenBlockList = checkTokenBlockList;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of("http://localhost:5173"
                ,"http://192.168.0.130:5173")
        );

        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        );

        configuration.setAllowedHeaders(
                List.of("*")
        );

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }





    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

         JwtAuthenticationFilter jwtAuthenticationFilter=
            new JwtAuthenticationFilter(
                    jwtValidator,
                    jwtAuthenticationEntryPoint,
                    checkTokenBlockList
            );


        http
                .cors(Customizer.withDefaults())
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
                        .requestMatchers("/admin/auth/**").hasAnyRole("ADMIN","MANAGER")
                        .requestMatchers("/admin/account/**").hasAnyRole("ADMIN","MANAGER")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

}