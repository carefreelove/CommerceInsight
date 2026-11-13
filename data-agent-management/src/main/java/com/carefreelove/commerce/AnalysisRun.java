/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.carefreelove.commerce;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.sql.Statement;

public class AnalysisRun {
    final String id;
    final int scope;
    final String question;
    final LocalDate referenceDate;
    final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(180);
    final List<Map<String,Object>> results=new ArrayList<>();
    List<Map<String,Object>> knowledge=List.of();
    Map<String,Object> plan=Map.of();
    String schema;
    Object shops;
    int calls;
    private boolean cancelled;
    private Future<?> modelRequest;
    private Statement statement;

    AnalysisRun(String id,int scope,String question,LocalDate referenceDate) {
        this.id=id;
        this.scope=scope;
        this.question=question;
        this.referenceDate=referenceDate;
    }

    synchronized void check() {
        if (cancelled || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("任务已取消");
        }
        if (System.nanoTime()>deadline) {
            throw new IllegalStateException("任务超过 180 秒时限");
        }
    }

    synchronized void model(Future<?> request) {
        if (cancelled) {
            request.cancel(true);
        }
        check();
        modelRequest=request;
    }

    synchronized void statement(Statement value) {
        check();
        statement=value;
    }

    synchronized void cancel() {
        cancelled=true;
        if (modelRequest!=null) {
            modelRequest.cancel(true);
        }
        if (statement!=null) {
            try {
                statement.cancel();
            }
            catch (java.sql.SQLException ignored) {
                // JDBC timeout and connection close remain active if cancel is unsupported.
            }
        }
    }
}
