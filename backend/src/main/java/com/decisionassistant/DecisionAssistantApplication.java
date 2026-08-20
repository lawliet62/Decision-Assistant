package com.decisionassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DecisionAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecisionAssistantApplication.class, args);
    }
}
