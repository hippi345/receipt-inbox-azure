package com.interview.receipt.worker;

import com.interview.receipt.config.ReceiptInfrastructureConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.observation.LettuceObservationAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = {
        DataRedisAutoConfiguration.class,
        DataRedisReactiveAutoConfiguration.class,
        DataRedisRepositoriesAutoConfiguration.class,
        LettuceObservationAutoConfiguration.class
})
@Import(ReceiptInfrastructureConfig.class)
public class ReceiptWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReceiptWorkerApplication.class, args);
    }
}
