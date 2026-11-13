/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.carefreelove.commerce;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.visitor.SQLASTVisitorAdapter;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class SqlGuard {
    private static final Set<String> TABLES=Set.of("shops","products","orders","order_items","refunds");
    private static final Set<String> FUNCTIONS=Set.of("sum","count","avg","min","max","round","abs",
            "coalesce","ifnull","nullif","if","date","date_format","year","month","day","dayofmonth",
            "date_sub","date_add","timestampdiff","datediff","concat","concat_ws","lower","upper",
            "cast","convert","greatest","least","row_number","rank","dense_rank","lag","lead");

    public String validate(String sql,String schema) {
        if (sql==null || sql.isBlank() || sql.length()>20000) {
            throw new IllegalArgumentException("SQL 为空或超出长度限制");
        }
        List<SQLStatement> statements=SQLUtils.parseStatements(sql,DbType.mysql);
        if (statements.size()!=1 || !(statements.get(0) instanceof SQLSelectStatement)) {
            throw new IllegalArgumentException("仅允许单条 SELECT 查询");
        }
        SQLStatement statement=statements.get(0);
        Set<String> ctes=new HashSet<>();
        statement.accept(new SQLASTVisitorAdapter() {
            @Override
            public boolean visit(SQLWithSubqueryClause.Entry entry) {
                ctes.add(clean(entry.getAlias()));
                return true;
            }
        });
        statement.accept(new SQLASTVisitorAdapter() {
            @Override
            public void preVisit(SQLObject node) {
                String kind=node.getClass().getSimpleName();
                if (kind.contains("OutFile") || kind.contains("DumpFile") || kind.contains("Assign")
                        || node instanceof SQLVariantRefExpr) {
                    throw new IllegalArgumentException("禁止文件操作、赋值和变量");
                }
                if (node instanceof SQLSelectQueryBlock block) {
                    if (block.getInto()!=null || block.isForUpdate()) {
                        throw new IllegalArgumentException("禁止导出和锁定查询");
                    }
                }
            }

            @Override
            public boolean visit(SQLExprTableSource table) {
                String name;
                if (table.getExpr() instanceof SQLIdentifierExpr id) {
                    name=clean(id.getName());
                }
                else if (table.getExpr() instanceof SQLPropertyExpr property
                        && property.getOwner() instanceof SQLIdentifierExpr owner) {
                    if (!clean(owner.getName()).equals(schema)) {
                        throw new IllegalArgumentException("禁止跨 Schema 查询");
                    }
                    name=clean(property.getName());
                }
                else {
                    throw new IllegalArgumentException("不支持的表引用");
                }
                if (!TABLES.contains(name) && !ctes.contains(name)) {
                    throw new IllegalArgumentException("未授权数据表 "+name);
                }
                return true;
            }

            @Override
            public boolean visit(SQLMethodInvokeExpr expression) {
                checkFunction(expression.getMethodName());
                if (expression.getOwner()!=null) {
                    throw new IllegalArgumentException("禁止限定名函数");
                }
                return true;
            }

            @Override
            public boolean visit(SQLAggregateExpr expression) {
                checkFunction(expression.getMethodName());
                return true;
            }
        });
        String normalized=SQLUtils.toSQLString(statement,DbType.mysql);
        if (normalized.toLowerCase(Locale.ROOT).contains("lock in share mode")) {
            throw new IllegalArgumentException("禁止锁定查询");
        }
        return normalized;
    }

    public void validateAggregation(String sql) {
        SQLUtils.parseSingleStatement(sql,DbType.mysql).accept(new SQLASTVisitorAdapter() {
            @Override
            public void preVisit(SQLObject object) {
                if (object instanceof SQLSelectQueryBlock block) {
                    validateBlock(block);
                }
            }
        });
    }

    private void validateBlock(SQLSelectQueryBlock block) {
        Set<String> filteredOrders=new HashSet<>();
        if (block.getFrom()==null) {
            return;
        }
        block.getFrom().accept(new SQLASTVisitorAdapter() {
            @Override
            public boolean visit(SQLSubqueryTableSource subquery) { return false; }

            @Override
            public boolean visit(SQLJoinTableSource join) {
                if (join.getJoinType()==SQLJoinTableSource.JoinType.LEFT_OUTER_JOIN
                        && join.getRight() instanceof SQLExprTableSource table
                        && table.getExpr() instanceof SQLIdentifierExpr name && clean(name.getName()).equals("orders")
                        && join.getCondition()!=null) {
                    String alias=clean(table.getAlias()==null?"orders":table.getAlias());
                    String condition=join.getCondition().toString().toLowerCase(Locale.ROOT);
                    if (condition.contains(alias+".paid_at") || condition.contains(alias+".status")) {
                        filteredOrders.add(alias);
                    }
                }
                return true;
            }
        });
        if (filteredOrders.isEmpty()) {
            return;
        }
        String where=block.getWhere()==null?"":block.getWhere().toString().toLowerCase(Locale.ROOT);
        boolean matchedRowsFiltered=filteredOrders.stream().allMatch(alias ->
                where.contains(alias+".id") || where.contains(alias+".status") || where.contains(alias+".paid_at"));
        if (matchedRowsFiltered) {
            return;
        }
        for (SQLSelectItem item:block.getSelectList()) {
            item.getExpr().accept(new SQLASTVisitorAdapter() {
                @Override
                public boolean visit(SQLQueryExpr query) { return false; }

                @Override
                public boolean visit(SQLAggregateExpr aggregate) {
                    if ("sum".equalsIgnoreCase(aggregate.getMethodName())) {
                        for (var argument:aggregate.getArguments()) {
                            if (argument instanceof SQLPropertyExpr property && "paid_amount".equals(clean(property.getName()))) {
                                throw new IllegalArgumentException("LEFT JOIN orders 的日期条件仅在 ON 中，直接 SUM 明细会纳入未匹配订单。请先过滤并聚合有效订单，再 LEFT JOIN 商品，或用 CASE 排除未匹配订单金额。");
                            }
                        }
                    }
                    return true;
                }
            });
        }
    }

    private static void checkFunction(String name) {
        if (!FUNCTIONS.contains(clean(name))) {
            throw new IllegalArgumentException("未允许的 SQL 函数 "+name);
        }
    }

    private static String clean(String name) {
        return name.replace("`","").toLowerCase(Locale.ROOT);
    }
}
