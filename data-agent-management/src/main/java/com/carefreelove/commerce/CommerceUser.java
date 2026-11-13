/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.carefreelove.commerce;

public record CommerceUser(long id, String username, String role, Integer shopId) {
    public boolean admin() {
        return "ADMIN".equals(role);
    }

    public void requireScope(int scope) {
        if (scope < 0 || scope > 2 || (!admin() && !Integer.valueOf(scope).equals(shopId))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "没有该店铺的数据权限");
        }
    }
}
