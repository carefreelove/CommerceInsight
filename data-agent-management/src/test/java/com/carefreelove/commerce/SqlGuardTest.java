/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.carefreelove.commerce;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SqlGuardTest {
    final SqlGuard guard=new SqlGuard();

    @ParameterizedTest
    @ValueSource(strings={
        "SELECT COUNT(*) FROM orders",
        "SELECT p.category,SUM(i.paid_amount) FROM products p JOIN order_items i ON i.product_id=p.id GROUP BY p.category",
        "WITH totals AS (SELECT SUM(paid_amount) AS amount FROM order_items) SELECT * FROM totals",
        "SELECT * FROM orders o WHERE EXISTS (SELECT 1 FROM refunds r JOIN order_items i ON r.order_item_id=i.id WHERE i.order_id=o.id)",
        "SELECT * FROM commerce_scope_1.orders",
        "SELECT SUM(i.paid_amount)/NULLIF(COUNT(DISTINCT o.id),0) FROM orders o JOIN order_items i ON o.id=i.order_id",
        "SELECT DATE_SUB(DATE('2026-11-01'),INTERVAL 1 MONTH)",
        "SELECT CASE WHEN COUNT(*)=0 THEN NULL ELSE SUM(paid_amount) END FROM order_items"
    })
    void permitsReadOnlyQueries(String sql) {
        assertThat(guard.validate(sql,"commerce_scope_1")).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings={
        "DELETE FROM orders", "UPDATE orders SET status='PAID'", "DROP TABLE orders",
        "SELECT * FROM orders; SELECT * FROM refunds", "SELECT * FROM commerce_data.orders",
        "SELECT * FROM commerce_scope_2.orders", "SELECT * FROM information_schema.tables",
        "SELECT * FROM orders WHERE id IN (SELECT id FROM commerce_data.orders)",
        "WITH leak AS (SELECT * FROM ci_users) SELECT * FROM leak",
        "SELECT * FROM orders UNION SELECT * FROM ci_users",
        "SELECT SLEEP(10)", "SELECT BENCHMARK(1000,1)", "SELECT LOAD_FILE('/etc/passwd')",
        "SELECT * FROM orders INTO OUTFILE '/tmp/leak'", "SELECT * FROM orders FOR UPDATE",
        "SELECT * FROM orders LOCK IN SHARE MODE", "SELECT @secret", "SELECT @@version",
        "SELECT commerce_app.some_function(1)", "CALL some_procedure()",
        "SELECT REPEAT('x',1000000)", "SELECT * FROM mysql.user"
    })
    void rejectsUnsafeQueries(String sql) {
        assertThatThrownBy(() -> guard.validate(sql,"commerce_scope_1")).isInstanceOf(RuntimeException.class);
    }
    @org.junit.jupiter.api.Test
    void catchesFilteredOuterJoinAmountLeak() {
        String unsafe="SELECT SUM(i.paid_amount) FROM products p LEFT JOIN order_items i ON i.product_id=p.id LEFT JOIN orders o ON o.id=i.order_id AND o.paid_at>='2026-10-01'";
        assertThatThrownBy(() -> guard.validateAggregation(unsafe)).isInstanceOf(IllegalArgumentException.class);
        guard.validateAggregation("SELECT SUM(CASE WHEN o.id IS NOT NULL THEN i.paid_amount ELSE 0 END) FROM products p LEFT JOIN order_items i ON i.product_id=p.id LEFT JOIN orders o ON o.id=i.order_id AND o.paid_at>='2026-10-01'");
        guard.validateAggregation("SELECT p.category,SUM(s.sales) FROM products p LEFT JOIN (SELECT i.product_id,SUM(i.paid_amount) sales FROM orders o JOIN order_items i ON o.id=i.order_id WHERE o.status='PAID' GROUP BY i.product_id) s ON s.product_id=p.id GROUP BY p.category");
    }
    @org.junit.jupiter.api.Test
    void rejectsTechnicalSchemaNameAsBusinessValue() {
        assertThatThrownBy(() -> guard.validateAggregation("SELECT COUNT(*) FROM shops WHERE name='commerce_scope_1'"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
