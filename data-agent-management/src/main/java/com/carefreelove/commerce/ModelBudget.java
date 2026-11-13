/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.carefreelove.commerce;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import java.sql.Statement;

@Component
public class ModelBudget {
    // USD peak/cache-miss rates, converted at a conservative 8 CNY/USD.
    static final BigDecimal INPUT=new BigDecimal("0.0000024");
    static final BigDecimal OUTPUT=new BigDecimal("0.0000096");
    static final int MAX_OUTPUT=4096;
    private final CommerceStore store;

    public ModelBudget(CommerceStore store) {
        this.store=store;
    }

    public record Reservation(long id,BigDecimal amount) { }

    public Reservation reserve(String task,String purpose,String model,int inputBytes) {
        BigDecimal amount=cost(inputBytes+1024,MAX_OUTPUT);
        return store.transaction().execute(tx -> {
            int allowed=store.jdbc().update("UPDATE ci_budget SET committed_cny=committed_cny+? "
                    +"WHERE id=1 AND committed_cny+?<=limit_cny",amount,amount);
            if (allowed!=1) {
                throw new IllegalStateException("项目模型预算不足，已停止新增调用");
            }
            var key=new GeneratedKeyHolder();
            store.jdbc().update(connection -> {
                var statement=connection.prepareStatement("INSERT INTO ci_model_calls(task_id,purpose,model,reserved_cny,status) VALUES(?,?,?,?,'RESERVED')",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1,task);
                statement.setString(2,purpose);
                statement.setString(3,model);
                statement.setBigDecimal(4,amount);
                return statement;
            },key);
            return new Reservation(key.getKey().longValue(),amount);
        });
    }

    public void settle(Reservation reservation,Integer input,Integer output) {
        if (input==null || output==null || input<=0) {
            store.jdbc().update("UPDATE ci_model_calls SET status='UNKNOWN' WHERE id=? AND status='RESERVED'",reservation.id());
            return;
        }
        BigDecimal charged=cost(input,output);
        store.transaction().executeWithoutResult(tx -> {
            int updated=store.jdbc().update("UPDATE ci_model_calls SET status='COMPLETED',charged_cny=?,input_tokens=?,output_tokens=? "
                    +"WHERE id=? AND status='RESERVED'",charged,input,output,reservation.id());
            if (updated==1) {
                store.jdbc().update("UPDATE ci_budget SET committed_cny=committed_cny+? WHERE id=1",charged.subtract(reservation.amount()));
            }
        });
    }

    static BigDecimal cost(int input,int output) {
        return INPUT.multiply(BigDecimal.valueOf(input)).add(OUTPUT.multiply(BigDecimal.valueOf(output)))
                .setScale(6,RoundingMode.CEILING);
    }
}
