/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.carefreelove.commerce;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.http.HttpStatus;

@Configuration
@EnableWebFluxSecurity
public class CommerceSecurity {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityWebFilterChain commerceFilterChain(ServerHttpSecurity http) {
        return http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .csrf(csrf -> csrf.csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler()))
                .authorizeExchange(auth -> auth
                        .pathMatchers("/api/auth/csrf","/api/auth/login","/api/health").permitAll()
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(new org.springframework.security.web.server.authorization.HttpStatusServerAccessDeniedHandler(HttpStatus.FORBIDDEN)))
                .build();
    }
}
