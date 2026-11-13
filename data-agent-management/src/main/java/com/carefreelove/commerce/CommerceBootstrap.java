/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.carefreelove.commerce;

import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;

@Component
public class CommerceBootstrap implements ApplicationRunner {
    private final CommerceStore store;
    private final PasswordEncoder passwords;
    private final String demoPassword;

    public CommerceBootstrap(CommerceStore store,PasswordEncoder passwords,
            @Value("${commerce.demo-password}") String demoPassword) {
        this.store=store;
        this.passwords=passwords;
        this.demoPassword=demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        store.jdbc().update("INSERT IGNORE INTO ci_users(username,password_hash,role,shop_id) VALUES(?,?,?,?)",
                "admin",passwords.encode(demoPassword),"ADMIN",null);
        for (int shop=1;shop<=2;shop++) {
            store.jdbc().update("INSERT IGNORE INTO ci_users(username,password_hash,role,shop_id) VALUES(?,?,?,?)",
                    "analyst"+shop,passwords.encode(demoPassword),"ANALYST",shop);
        }
        store.transaction().executeWithoutResult(tx -> {
            var tasks=store.jdbc().queryForList("SELECT id FROM ci_tasks WHERE status IN ('QUEUED','RUNNING') FOR UPDATE");
            for (var task:tasks) {
                store.finish((String)task.get("id"),"FAILED","服务重启导致任务中断，请手动重试");
            }
        });
    }
}
