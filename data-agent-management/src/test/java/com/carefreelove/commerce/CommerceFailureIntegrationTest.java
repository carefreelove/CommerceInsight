/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.carefreelove.commerce;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;

@EnabledIfEnvironmentVariable(named="CI_INTEGRATION",matches="true")
@ActiveProfiles("commerce")
@SpringBootTest(classes=CommerceInsightApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"commerce.mock-model=true"})
class CommerceFailureIntegrationTest {
    @Autowired CommerceStore store;
    @Autowired AnalysisService tasks;
    @Autowired CommerceBootstrap bootstrap;
    @Autowired ScopedQueries queries;
    @MockBean CommerceModel model;

    @BeforeEach
    void identifyTestModel() {
        when(model.modelName()).thenReturn("TEST_FAILURE");
    }

    @Test
    void upstreamModelFailureIsPersisted() throws Exception {
        when(model.call(any(),anyString(),anyString(),anyString())).thenThrow(new IllegalStateException("模拟模型服务不可用"));
        var user=store.user("analyst1");
        var task=tasks.create(user,"模拟模型失败",1,LocalDate.of(2026,11,13),UUID.randomUUID().toString(),null);
        String id=(String)task.get("id");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var result=store.task(id,user);
            assertThat(result.get("status")).isEqualTo("FAILED");
            assertThat(result.get("error_message").toString()).contains("模拟模型服务不可用");
        });
    }

    @Test
    void cancellationWinsAgainstLateModelCompletion() throws Exception {
        var entered=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        when(model.call(any(),anyString(),anyString(),anyString())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(10,TimeUnit.SECONDS);
            return "{}";
        });
        var user=store.user("analyst1");
        var task=tasks.create(user,"模拟执行中取消",1,LocalDate.of(2026,11,13),UUID.randomUUID().toString(),null);
        String id=(String)task.get("id");
        try {
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            assertThat(tasks.cancel(id,user).get("status")).isEqualTo("CANCELLED");
        }
        finally {
            release.countDown();
        }
        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(store.task(id,user).get("status")).isEqualTo("CANCELLED"));
    }

    @Test
    void startupRecoversOrphanedTasks() {
        var user=store.user("analyst1");
        String id=UUID.randomUUID().toString();
        store.jdbc().update("INSERT INTO ci_tasks(id,user_id,scope_id,question,reference_date,status,idempotency_key) VALUES(?,?,1,'restart recovery','2026-11-13','RUNNING',?)",id,user.id(),id);
        bootstrap.run(new DefaultApplicationArguments());
        assertThat(store.task(id,user).get("status")).isEqualTo("FAILED");
        assertThat(store.task(id,user).get("error_message").toString()).contains("服务重启");
    }

    @Test
    void expensiveReadOnlyQueryIsTerminatedByJdbcTimeout() {
        String sql="SELECT COUNT(*) FROM order_items a CROSS JOIN order_items b CROSS JOIN order_items c CROSS JOIN order_items d CROSS JOIN order_items e";
        long start=System.nanoTime();
        assertThatThrownBy(() -> queries.query(1,sql,null)).isInstanceOf(java.sql.SQLException.class);
        assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(40));
    }
}
