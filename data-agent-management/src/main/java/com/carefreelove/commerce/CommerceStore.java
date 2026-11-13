/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.carefreelove.commerce;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@Repository
public class CommerceStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;

    public CommerceStore(JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.json = json;
        this.transaction = new TransactionTemplate(manager);
    }

    public JdbcTemplate jdbc() { return jdbc; }

    public ObjectMapper json() { return json; }

    public TransactionTemplate transaction() { return transaction; }

    public String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("结果序列化失败", e);
        }
    }

    public CommerceUser user(String username) {
        return jdbc.queryForObject("SELECT id,username,role,shop_id FROM ci_users WHERE username=?",
                (r,n) -> new CommerceUser(r.getLong(1),r.getString(2),r.getString(3),r.getObject(4,Integer.class)), username);
    }

    public Map<String,Object> task(String id, CommerceUser user) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT * FROM ci_tasks WHERE id=? AND user_id=?",id,user.id());
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"任务不存在");
        }
        Map<String,Object> task = rows.get(0);
        user.requireScope(((Number) task.get("scope_id")).intValue());
        return task;
    }

    public List<Map<String,Object>> history(CommerceUser user, int page) {
        if (user.admin()) {
            return jdbc.queryForList("SELECT * FROM ci_tasks WHERE user_id=? ORDER BY created_at DESC LIMIT 20 OFFSET ?",
                    user.id(), page*20);
        }
        return jdbc.queryForList("SELECT * FROM ci_tasks WHERE user_id=? AND scope_id=? ORDER BY created_at DESC LIMIT 20 OFFSET ?",
                user.id(),user.shopId(),page*20);
    }

    public List<Map<String,Object>> evidence(String id) {
        return jdbc.queryForList("SELECT id,sql_text,result_json,elapsed_ms,created_at FROM ci_evidence WHERE task_id=? ORDER BY id", id);
    }

    public List<Map<String,Object>> events(String id, long after) {
        return jdbc.queryForList("SELECT id,type,payload,created_at FROM ci_events WHERE task_id=? AND id>? ORDER BY id LIMIT 100",id,after);
    }

    public void event(String id,String type,Object payload) {
        jdbc.update("INSERT INTO ci_events(task_id,type,payload) VALUES(?,?,?)",id,type,encode(payload));
    }

    public void active(String id) {
        String status = jdbc.queryForObject("SELECT status FROM ci_tasks WHERE id=?",String.class,id);
        if (!"RUNNING".equals(status)) {
            throw new java.util.concurrent.CancellationException("任务已结束");
        }
    }

    public boolean finish(String id, String status, String error) {
        return Boolean.TRUE.equals(transaction.execute(tx -> {
            int count = jdbc.update("UPDATE ci_tasks SET status=?,error_message=? WHERE id=? AND status IN ('QUEUED','RUNNING')",
                    status,error,id);
            if (count==1) {
                event(id,"terminal",Map.of("status",status,"message",error==null?"分析完成":error));
            }
            return count==1;
        }));
    }
}
