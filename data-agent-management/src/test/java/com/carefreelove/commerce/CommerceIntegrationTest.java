/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.carefreelove.commerce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="CI_INTEGRATION",matches="true")
@ActiveProfiles("commerce")
@SpringBootTest(classes=CommerceInsightApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"commerce.mock-model=true"})
class CommerceIntegrationTest {
    @Autowired CommerceStore store;
    @Autowired ScopedQueries queries;
    @Autowired AnalysisService tasks;
    @Autowired ModelBudget budget;

    @Test
    void repositoryProxyAndBootstrapAreInitialized() {
        assertThat(store.jdbc()).isNotNull();
        assertThat(store.user("analyst1").shopId()).isEqualTo(1);
        assertThat(store.user("admin").admin()).isTrue();
    }

    @Test
    void databaseViewsEnforceScopeAndRefundDeduplication() throws Exception {
        var result=queries.query(1,"SELECT DISTINCT shop_id FROM orders",null);
        assertThat(result.get("rows").toString()).isEqualTo("[{shop_id=1}]");
        var refunds=queries.query(1,"SELECT COUNT(*) AS total,COUNT(DISTINCT refund_no) AS unique_count FROM refunds",null);
        var row=((java.util.List<java.util.Map<String,Object>>)refunds.get("rows")).get(0);
        assertThat(row.get("total")).isEqualTo(row.get("unique_count"));
        assertThatThrownBy(() -> queries.query(1,"SELECT * FROM commerce_data.orders",null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void taskCompletesAndIdempotencyIsStable() throws Exception {
        var user=store.user("analyst1");
        String key=UUID.randomUUID().toString();
        var created=tasks.create(user,"上个月各品类销售额",1,LocalDate.of(2026,11,13),key,null);
        String id=(String)created.get("id");
        var duplicate=tasks.create(user,"上个月各品类销售额",1,LocalDate.of(2026,11,13),key,null);
        assertThat(duplicate.get("id")).isEqualTo(id);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(store.task(id,user).get("status")).isEqualTo("SUCCEEDED"));
        assertThat(store.evidence(id)).hasSize(1);
        assertThat(store.events(id,0)).isNotEmpty();
        assertThatThrownBy(() -> store.task(id,store.user("analyst2"))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void competingTerminalUpdatesHaveOneWinner() {
        var user=store.user("analyst1");
        String id=UUID.randomUUID().toString();
        store.jdbc().update("INSERT INTO ci_tasks(id,user_id,scope_id,question,reference_date,status,idempotency_key) VALUES(?,?,1,'terminal race','2026-11-13','RUNNING',?)",id,user.id(),id);
        var outcomes=java.util.stream.IntStream.range(0,8).parallel().mapToObj(index -> store.finish(id,"CANCELLED","test"))
                .toList();
        assertThat(outcomes.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        assertThat(store.events(id,0).stream().filter(event -> "terminal".equals(event.get("type"))).count()).isEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void budgetReservationIsAtomicAndSettlesOnce() {
        var reservation=budget.reserve("integration-budget","test","TEST_STUB",1000);
        budget.settle(reservation,100,10);
        var before=store.jdbc().queryForObject("SELECT committed_cny FROM ci_budget WHERE id=1",java.math.BigDecimal.class);
        budget.settle(reservation,100,10);
        assertThat(store.jdbc().queryForObject("SELECT committed_cny FROM ci_budget WHERE id=1",java.math.BigDecimal.class)).isEqualByComparingTo(before);
        store.transaction().executeWithoutResult(tx -> {
            store.jdbc().update("UPDATE ci_budget SET limit_cny=committed_cny WHERE id=1");
            assertThatThrownBy(() -> budget.reserve("integration-budget","test","TEST_STUB",1000)).isInstanceOf(IllegalStateException.class);
            tx.setRollbackOnly();
        });
    }
}
