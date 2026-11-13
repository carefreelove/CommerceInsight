/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.carefreelove.commerce;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;

class CommerceDomainTest {
    @Test
    void analystScopeIsRestricted() {
        var user=new CommerceUser(1,"analyst1","ANALYST",1);
        user.requireScope(1);
        assertThatThrownBy(() -> user.requireScope(2)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> user.requireScope(0)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void adminUsesOnlyKnownScopes() {
        var user=new CommerceUser(1,"admin","ADMIN",null);
        user.requireScope(0);
        user.requireScope(2);
        assertThatThrownBy(() -> user.requireScope(3)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void cancelledRunCancelsRequest() {
        var run=new AnalysisRun("test",1,"问题",LocalDate.of(2026,11,13));
        var future=new CompletableFuture<String>();
        run.model(future);
        run.cancel();
        assertThat(future.isCancelled()).isTrue();
        assertThatThrownBy(run::check).isInstanceOf(java.util.concurrent.CancellationException.class);
    }

    @Test
    void lateModelRegistrationIsCancelled() {
        var run=new AnalysisRun("test",1,"问题",LocalDate.of(2026,11,13));
        run.cancel();
        var future=new CompletableFuture<String>();
        assertThatThrownBy(() -> run.model(future)).isInstanceOf(java.util.concurrent.CancellationException.class);
        assertThat(future.isCancelled()).isTrue();
    }

    @Test
    void pricingUsesConservativePeakRates() {
        assertThat(ModelBudget.cost(1000000,1000000)).isEqualByComparingTo("12.000000");
        assertThat(ModelBudget.cost(0,0)).isEqualByComparingTo("0");
    }

    @Test
    void dictionaryIncludesCoreConstraints() {
        var results=new MetricCatalog().retrieve("上个月退款率");
        assertThat(results).hasSize(5);
        assertThat(results.stream().filter(item -> !((java.util.List<?>)item.get("matchedAliases")).isEmpty()).count()).isGreaterThan(0);
        assertThat(new MetricCatalog().all()).allMatch(metric -> metric.version().equals("1.0"));
    }
}
