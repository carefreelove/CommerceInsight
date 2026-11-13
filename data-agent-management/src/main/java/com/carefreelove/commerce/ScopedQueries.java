/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.carefreelove.commerce;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.sql.*;
import java.util.*;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;

@Component
public class ScopedQueries {
    private final String host;
    private final int port;
    private final String password;
    private final SqlGuard guard;

    public ScopedQueries(@Value("${commerce.db-host}") String host,@Value("${commerce.db-port}") int port,
            @Value("${commerce.read-password}") String password,SqlGuard guard) {
        this.host=host;
        this.port=port;
        this.password=password;
        this.guard=guard;
    }

    public String schemaName(int scope) {
        if (scope<0 || scope>2) {
            throw new IllegalArgumentException("无效店铺范围");
        }
        return "commerce_scope_"+(scope==0?"all":scope);
    }

    private Connection connection(int scope) throws SQLException {
        String suffix=scope==0?"all":String.valueOf(scope);
        Connection connection=DriverManager.getConnection("jdbc:mysql://"+host+":"+port+"/"+schemaName(scope)
                +"?serverTimezone=Asia/Shanghai&allowMultiQueries=false&allowPublicKeyRetrieval=true&useSSL=false&socketTimeout=35000",
                "ci_read_"+suffix,password);
        connection.setReadOnly(true);
        return connection;
    }

    public String schema(int scope) throws SQLException {
        StringBuilder result=new StringBuilder();
        try (Connection connection=connection(scope);
                ResultSet columns=connection.getMetaData().getColumns(schemaName(scope),null,"%",null)) {
            while (columns.next()) {
                result.append(columns.getString("TABLE_NAME")).append('.').append(columns.getString("COLUMN_NAME"))
                        .append(' ').append(columns.getString("TYPE_NAME")).append('\n');
            }
        }
        return result.toString();
    }

    public Map<String,Object> query(int scope,String rawSql,AnalysisRun run) throws SQLException {
        String sql=guard.validate(rawSql,schemaName(scope));
        guard.validateAggregation(sql);
        long started=System.nanoTime();
        try (Connection connection=connection(scope);Statement statement=connection.createStatement()) {
            statement.setMaxRows(SqlExecutor.RESULT_SET_LIMIT+1);
            statement.setQueryTimeout(SqlExecutor.STATEMENT_TIMEOUT);
            if (run!=null) {
                run.statement(statement);
            }
            try (ResultSet result=statement.executeQuery(sql)) {
                List<String> columns=new ArrayList<>();
                for (int i=1;i<=result.getMetaData().getColumnCount();i++) {
                    columns.add(result.getMetaData().getColumnLabel(i));
                }
                if (new HashSet<>(columns).size()!=columns.size()) {
                    throw new IllegalArgumentException("结果列名重复，请使用唯一别名");
                }
                List<Map<String,Object>> rows=new ArrayList<>();
                while (result.next()) {
                    Map<String,Object> row=new LinkedHashMap<>();
                    for (int i=1;i<=columns.size();i++) {
                        Object value=result.getObject(i);
                        if (value instanceof java.time.temporal.TemporalAccessor || value instanceof java.util.Date) {
                            value=value.toString();
                        }
                        row.put(columns.get(i-1),value);
                    }
                    rows.add(row);
                }
                boolean truncated=rows.size()>SqlExecutor.RESULT_SET_LIMIT;
                if (truncated) {
                    rows.remove(rows.size()-1);
                }
                Map<String,Object> response=new LinkedHashMap<>();
                response.put("sql",sql);
                response.put("columns",columns);
                response.put("rows",rows);
                response.put("truncated",truncated);
                response.put("elapsedMs",(System.nanoTime()-started)/1000000);
                return response;
            }
        }
    }
}
