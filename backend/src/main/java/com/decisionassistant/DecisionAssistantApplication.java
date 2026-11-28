package com.decisionassistant;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import com.decisionassistant.job.JobMode;

// 예약 작업은 job mode가 아닐 때만 job.SchedulingConfiguration이 활성화한다.
@SpringBootApplication
@ConfigurationPropertiesScan
@OpenAPIDefinition(security = @SecurityRequirement(name = "sessionCookie"))
@SecurityScheme(
        name = "sessionCookie",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = "DECISION_ASSISTANT_SESSION"
)
public class DecisionAssistantApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(DecisionAssistantApplication.class, args);
        // job은 ApplicationRunner에서 끝난다. 남은 non-daemon thread가 JVM을 붙잡지 않도록 종료 코드로 끝낸다.
        if (JobMode.active(context.getEnvironment())) System.exit(SpringApplication.exit(context));
    }
}
