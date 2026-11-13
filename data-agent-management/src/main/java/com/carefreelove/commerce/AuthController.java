/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.carefreelove.commerce;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final CommerceStore store;
    private final PasswordEncoder passwords;
    private final WebSessionServerSecurityContextRepository contexts = new WebSessionServerSecurityContextRepository();
    private final String dummyHash;

    public AuthController(CommerceStore store,PasswordEncoder passwords) {
        this.store=store;
        this.passwords=passwords;
        this.dummyHash=passwords.encode("invalid-password-placeholder");
    }

    @GetMapping("/health")
    Map<String,String> health() {
        return Map.of("status","UP","application","CommerceInsight");
    }

    @GetMapping("/auth/csrf")
    Mono<Map<String,String>> csrf(ServerWebExchange exchange) {
        Mono<CsrfToken> token=exchange.getAttribute(CsrfToken.class.getName());
        return token.map(t -> Map.of("token",t.getToken(),"headerName",t.getHeaderName()));
    }

    public record Login(@NotBlank @Size(max=60) String username, @NotBlank @Size(max=200) String password) { }

    @PostMapping("/auth/login")
    Mono<CommerceUser> login(@Valid @RequestBody Login input,ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            var rows=store.jdbc().queryForList("SELECT password_hash FROM ci_users WHERE username=?",input.username());
            String hash=rows.isEmpty()?dummyHash:(String)rows.get(0).get("password_hash");
            if (!passwords.matches(input.password(),hash) || rows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"用户名或密码错误");
            }
            return store.user(input.username());
        }).subscribeOn(Schedulers.boundedElastic()).flatMap(user -> {
            var principal=User.withUsername(user.username()).password("").roles(user.role()).build();
            var authentication=UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities());
            return exchange.getSession().flatMap(session -> session.changeSessionId())
                    .then(contexts.save(exchange,new SecurityContextImpl(authentication))).thenReturn(user);
        });
    }

    @GetMapping("/auth/me")
    Mono<CommerceUser> me(Principal principal) {
        return Mono.fromCallable(() -> store.user(principal.getName())).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/auth/logout")
    Mono<Void> logout(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> session.invalidate());
    }
}
