/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.carefreelove.commerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CommerceInsightApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CommerceInsightApplication.class);
        app.setAdditionalProfiles("commerce");
        app.run(args);
    }
}
