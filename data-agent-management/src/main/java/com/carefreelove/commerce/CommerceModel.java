/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.carefreelove.commerce;

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.DynamicModelFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;

@Component
public class CommerceModel {
    private final ChatModel model;
    private final String modelName;
    private final ModelBudget budget;
    private final boolean mock;

    public CommerceModel(ModelBudget budget,@Value("${commerce.model-key}") String key,
            @Value("${commerce.model-base-url}") String baseUrl,@Value("${commerce.model-name}") String modelName,
            @Value("${commerce.mock-model:false}") boolean mock) {
        this.budget=budget;
        this.modelName=modelName;
        this.mock=mock;
        if (!mock && !"deepseek-flash".equals(modelName)) {
            throw new IllegalArgumentException("当前预算计价仅支持 deepseek-flash");
        }
        ModelConfigDTO config=new ModelConfigDTO();
        config.setProvider("custom");
        config.setBaseUrl(baseUrl);
        config.setApiKey(key);
        config.setModelName(modelName);
        config.setCompletionsPath("/chat/completions");
        config.setMaxTokens(ModelBudget.MAX_OUTPUT);
        config.setTemperature(0.0);
        this.model=mock || key.isBlank()?null:new DynamicModelFactory().createChatModel(config,
                org.springframework.retry.support.RetryTemplate.builder().maxAttempts(1).build());
    }

    public boolean configured() {
        return model!=null || mock;
    }

    public String modelName() {
        return mock?"TEST_STUB":modelName;
    }

    public String call(AnalysisRun run,String purpose,String system,String user) throws Exception {
        run.check();
        if (++run.calls>12) {
            throw new IllegalStateException("任务超过 12 次模型调用限制");
        }
        if (mock) {
            return mockResponse(purpose);
        }
        if (model==null) {
            throw new IllegalStateException("请在启动环境配置 CI_MODEL_KEY 或 ANTHROPIC_AUTH_TOKEN");
        }
        int bytes=(system+user).getBytes(StandardCharsets.UTF_8).length;
        if (bytes>48000) {
            throw new IllegalArgumentException("模型上下文超过 48000 字节限制");
        }
        var reservation=budget.reserve(run.id,purpose,modelName,bytes);
        boolean settled=false;
        try {
            var options=OpenAiChatOptions.builder().model(modelName).maxTokens(ModelBudget.MAX_OUTPUT)
                    .temperature(0.0).streamUsage(true)
                    .extraBody(Map.of("thinking",Map.of("type","disabled"))).build();
            var prompt=new Prompt(List.of(new SystemMessage(system),new UserMessage(user)),options);
            var request=model.stream(prompt).collectList().toFuture();
            run.model(request);
            long remaining=Math.max(1,TimeUnit.NANOSECONDS.toMillis(run.deadline-System.nanoTime()));
            List<ChatResponse> chunks=request.get(remaining,TimeUnit.MILLISECONDS);
            StringBuilder answer=new StringBuilder();
            Integer input=null;
            Integer output=null;
            for (ChatResponse chunk:chunks) {
                if (chunk.getResult()!=null && chunk.getResult().getOutput().getText()!=null) {
                    answer.append(chunk.getResult().getOutput().getText());
                }
                var usage=chunk.getMetadata().getUsage();
                if (usage!=null && usage.getPromptTokens()!=null && usage.getPromptTokens()>0) {
                    input=usage.getPromptTokens();
                    output=usage.getCompletionTokens();
                }
            }
            budget.settle(reservation,input,output);
            settled=true;
            run.check();
            return answer.toString();
        }
        finally {
            if (!settled) {
                budget.settle(reservation,null,null);
            }
        }
    }

    private String mockResponse(String purpose) {
        if (purpose.equals("report")) {
            return "{\"observations\":[{\"text\":\"这是自动测试模型桩输出，请查看实际查询结果表。\",\"evidence\":[1]}],\"hypotheses\":[],\"unconfirmed\":[\"模型桩用于流程测试\"]}";
        }
        return "{\"dateRange\":{\"start\":\"2026-10-01\",\"endExclusive\":\"2026-11-01\"},\"steps\":[{\"tool\":\"SQL\",\"purpose\":\"按品类计算销售额\",\"sql\":\"SELECT p.category, SUM(i.paid_amount) AS sales FROM orders o JOIN order_items i ON o.id=i.order_id JOIN products p ON i.product_id=p.id WHERE o.status='PAID' AND o.paid_at>='2026-10-01' AND o.paid_at<'2026-11-01' GROUP BY p.category ORDER BY p.category\"}]}";
    }
}
