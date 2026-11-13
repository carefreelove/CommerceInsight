/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.carefreelove.commerce;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.time.LocalDate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

@RestController
@RequestMapping("/api")
public class AnalysisController {
    private final CommerceStore store;
    private final AnalysisService tasks;
    private final ScopedQueries queries;
    private final MetricCatalog metrics;
    private final CommerceModel model;

    public AnalysisController(CommerceStore store,AnalysisService tasks,ScopedQueries queries,MetricCatalog metrics,CommerceModel model) {
        this.store=store;
        this.tasks=tasks;
        this.queries=queries;
        this.metrics=metrics;
        this.model=model;
    }

    private <T> Mono<T> blocking(Principal principal,Function<CommerceUser,T> work) {
        return Mono.fromCallable(() -> work.apply(store.user(principal.getName()))).subscribeOn(Schedulers.boundedElastic());
    }

    public record CreateTask(@NotBlank @Size(max=2000) String question, @Min(0) @Max(2) int scopeId,
            @NotNull LocalDate referenceDate) { }

    @PostMapping("/analysis/tasks")
    Mono<Map<String,Object>> create(Principal principal,@Valid @RequestBody CreateTask input,
            @RequestHeader("Idempotency-Key") @Size(max=100) String key) {
        return blocking(principal,user -> tasks.create(user,input.question(),input.scopeId(),input.referenceDate(),key,null));
    }

    @GetMapping("/analysis/tasks")
    Mono<List<Map<String,Object>>> history(Principal principal,@RequestParam(defaultValue="0") int page) {
        if (page<0 || page>100000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"页码无效");
        }
        return blocking(principal,user -> store.history(user,page));
    }

    @GetMapping("/analysis/tasks/{id}")
    Mono<Map<String,Object>> task(Principal principal,@PathVariable String id) {
        return blocking(principal,user -> {
            var task=store.task(id,user);
            task.put("evidence",store.evidence(id));
            task.put("calls",store.jdbc().queryForList("SELECT purpose,model,reserved_cny,charged_cny,input_tokens,output_tokens,status FROM ci_model_calls WHERE task_id=? ORDER BY id",id));
            return task;
        });
    }

    @PostMapping("/analysis/tasks/{id}/cancel")
    Mono<Map<String,Object>> cancel(Principal principal,@PathVariable String id) {
        return blocking(principal,user -> tasks.cancel(id,user));
    }

    @PostMapping("/analysis/tasks/{id}/retry")
    Mono<Map<String,Object>> retry(Principal principal,@PathVariable String id,@RequestHeader("Idempotency-Key") String key) {
        return blocking(principal,user -> {
            var task=store.task(id,user);
            if (List.of("QUEUED","RUNNING").contains(task.get("status"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,"请先结束当前任务");
            }
            return tasks.create(user,(String)task.get("question"),((Number)task.get("scope_id")).intValue(),
                    LocalDate.parse(task.get("reference_date").toString()),key,id);
        });
    }

    @GetMapping("/analysis/tasks/{id}/report")
    Mono<Map<String,Object>> report(Principal principal,@PathVariable String id) {
        return blocking(principal,user -> {
            var task=store.task(id,user);
            if (!"SUCCEEDED".equals(task.get("status"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,"报告尚未完成");
            }
            return Map.of("task",task,"evidence",store.evidence(id));
        });
    }

    @GetMapping(value="/analysis/tasks/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<String>> events(Principal principal,@PathVariable String id,
            @RequestHeader(value="Last-Event-ID",defaultValue="0") long after) {
        AtomicLong cursor=new AtomicLong(Math.max(0,after));
        return blocking(principal,user -> store.task(id,user)).flatMapMany(ignored ->
                Flux.interval(Duration.ZERO,Duration.ofMillis(700)).concatMap(tick -> blocking(principal,user -> {
                    var task=store.task(id,user);
                    var events=store.events(id,cursor.get());
                    List<ServerSentEvent<String>> batch=new ArrayList<>();
                    for (var event:events) {
                        cursor.set(((Number)event.get("id")).longValue());
                        batch.add(ServerSentEvent.<String>builder((String)event.get("payload"))
                                .id(String.valueOf(cursor.get())).event((String)event.get("type")).build());
                    }
                    if (events.isEmpty() && !List.of("QUEUED","RUNNING").contains(task.get("status"))) {
                        batch.add(ServerSentEvent.<String>builder(store.encode(Map.of("status",task.get("status"))))
                                .event("closed").build());
                    }
                    if (batch.isEmpty()) {
                        batch.add(ServerSentEvent.<String>builder().comment("heartbeat").build());
                    }
                    return batch;
                })).flatMapIterable(Function.identity()).takeUntil(event -> "closed".equals(event.event())));
    }

    @GetMapping("/metrics")
    List<MetricCatalog.Metric> metrics() {
        return metrics.all();
    }

    @GetMapping("/overview")
    Mono<Map<String,Object>> overview(Principal principal,@RequestParam(defaultValue="1") int scopeId) {
        return blocking(principal,user -> {
            user.requireScope(scopeId);
            try {
                return queries.query(scopeId,"SELECT DATE_FORMAT(o.paid_at,'%Y-%m') AS month, ROUND(SUM(i.paid_amount),2) AS sales, "
                        +"COUNT(DISTINCT o.id) AS paid_orders FROM orders o JOIN order_items i ON i.order_id=o.id "
                        +"WHERE o.status='PAID' GROUP BY DATE_FORMAT(o.paid_at,'%Y-%m') ORDER BY month",null);
            }
            catch (java.sql.SQLException error) {
                throw new IllegalStateException("经营概览查询失败",error);
            }
        });
    }

    @GetMapping("/admin/config")
    Mono<Map<String,Object>> admin(Principal principal) {
        return blocking(principal,user -> Map.of("model",model.modelName(),"modelConfigured",model.configured(),
                "budget",store.jdbc().queryForMap("SELECT * FROM ci_budget WHERE id=1"),
                "users",store.jdbc().queryForList("SELECT id,username,role,shop_id FROM ci_users ORDER BY id"),
                "metrics",metrics.all()));
    }

    public record Grant(@NotNull @Min(1) @Max(2) Integer shopId) { }

    @PatchMapping("/admin/users/{id}/scope")
    Mono<Map<String,Object>> grant(Principal principal,@PathVariable long id,@Valid @RequestBody Grant input) {
        return blocking(principal,user -> store.transaction().execute(tx -> {
            store.jdbc().queryForList("SELECT id FROM ci_users WHERE id=? FOR UPDATE",id);
            Integer active=store.jdbc().queryForObject("SELECT COUNT(*) FROM ci_tasks WHERE user_id=? AND status IN ('QUEUED','RUNNING')",Integer.class,id);
            if (active!=null && active>0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,"用户仍有运行任务，请结束后调整授权");
            }
            int changed=store.jdbc().update("UPDATE ci_users SET shop_id=? WHERE id=? AND role='ANALYST'",input.shopId(),id);
            if (changed!=1) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,"分析员不存在");
            }
            return Map.of("id",id,"shopId",input.shopId());
        }));
    }
}
