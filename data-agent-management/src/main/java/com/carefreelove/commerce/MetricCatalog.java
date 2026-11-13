/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.carefreelove.commerce;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class MetricCatalog {
    public record Metric(String id,String name,List<String> aliases,String version,String definition,String timeBasis) { }
    private final List<Metric> metrics=List.of(
        new Metric("sales","销售额",List.of("销售","营收","实付","收入"),"1.0",
            "SUM(order_items.paid_amount)，仅 orders.status='PAID'，关联订单和明细。金额不乘 quantity，paid_amount 已为明细实付合计。","orders.paid_at"),
        new Metric("refund_amount","退款金额",List.of("退款","退货"),"1.0",
            "SUM(refunds.amount)，refunds 视图已按 refund_no 去重且仅含成功退款；每次成功的部分退款分别计入。","refunds.succeeded_at"),
        new Metric("refund_rate","订单退款率",List.of("退款率","退货率"),"1.0",
            "统计期支付订单中，截至分析时点存在成功退款的去重订单数 / 同期支付订单去重数。品类维度退款必须关联该品类明细，使用 EXISTS 或先聚合退款避免 JOIN 放大。分母为零返回 NULL。","支付时间划定订单队列，退款成功时间小于分析时点次日"),
        new Metric("net_sales","净销售额",List.of("净销售","净收入"),"1.0",
            "期间销售额减期间成功退款金额，分别按支付时间和退款成功时间归属。跨期退款会使该指标与支付队列净额不同。","期间现金流口径"),
        new Metric("paid_orders","支付订单数",List.of("订单","客单价"),"1.0",
            "COUNT(DISTINCT orders.id)，仅成功支付订单；客单价=销售额/NULLIF(支付订单数,0)。","orders.paid_at")
    );

    public List<Metric> all() {
        return metrics;
    }

    public List<Map<String,Object>> retrieve(String question) {
        return metrics.stream().map(metric -> {
            List<String> matches=metric.aliases().stream().filter(question::contains).toList();
            return Map.<String,Object>of("metric",metric,"matchedAliases",matches,
                    "reason",matches.isEmpty()?"核心口径约束":"名称或别名匹配");
        }).toList();
    }
}
