package com.interview.receipt.config;

import com.interview.receipt.logging.CorrelationHeaderPolicy;
import com.azure.monitor.opentelemetry.autoconfigure.AzureMonitorAutoConfigure;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.QueueServiceClientBuilder;
import com.interview.receipt.NoopReceiptTelemetry;
import com.interview.receipt.ReceiptBlobs;
import com.interview.receipt.ReceiptCache;
import com.interview.receipt.ReceiptEntity;
import com.interview.receipt.ReceiptJobs;
import com.interview.receipt.ReceiptTable;
import com.interview.receipt.ReceiptTelemetry;
import com.interview.receipt.azure.AzureReceiptBlobs;
import com.interview.receipt.azure.AzureReceiptJobs;
import com.interview.receipt.azure.AzureReceiptTable;
import com.interview.receipt.logging.ActivityLogAttachment;
import com.interview.receipt.redis.RedisReceiptCache;
import com.interview.receipt.telemetry.OpenTelemetryReceiptTelemetry;
import ch.qos.logback.classic.LoggerContext;
import io.lettuce.core.resource.ClientResources;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(ReceiptProperties.class)
public class ReceiptInfrastructureConfig {
    private static final String CONTAINER = "receipts";
    private static final String TABLE = "receipts";
    private static final String JOBS_QUEUE = "receipt-jobs";
    private static final String POISON_QUEUE = "receipt-jobs-poison";

    @Bean
    ActivityLogAttachment activityLog(Environment environment) throws java.io.IOException {
        return new ActivityLogAttachment(environment.getProperty("spring.application.name", "receipt"));
    }

    @Bean
    ReceiptBlobs receiptBlobs(ReceiptProperties properties, OpenTelemetry openTelemetry) {
        BlobContainerClient container = blobServiceClientBuilder(storageConnectionString(properties))
                .buildClient()
                .getBlobContainerClient(CONTAINER);
        container.createIfNotExists();
        return new AzureReceiptBlobs(container);
    }

    @Bean
    ReceiptTable receiptTable(ReceiptProperties properties, OpenTelemetry openTelemetry) {
        TableClient table = tableServiceClientBuilder(storageConnectionString(properties))
                .buildClient()
                .createTableIfNotExists(TABLE);
        return new AzureReceiptTable(table);
    }

    @Bean
    ReceiptJobs receiptJobs(ReceiptProperties properties, OpenTelemetry openTelemetry) {
        QueueServiceClient queues = queueServiceClientBuilder(storageConnectionString(properties))
                .buildClient();
        QueueClient jobs = queues.getQueueClient(JOBS_QUEUE);
        QueueClient poison = queues.getQueueClient(POISON_QUEUE);
        jobs.createIfNotExists();
        poison.createIfNotExists();
        return new AzureReceiptJobs(jobs, poison, receiptJson());
    }

    @Bean
    @ConditionalOnProperty(prefix = "receipt.redis", name = "enabled", havingValue = "true")
    LettuceConnectionFactory receiptRedisConnectionFactory(ReceiptProperties properties, OpenTelemetry openTelemetry) {
        ReceiptProperties.Redis redis = properties.getRedis();
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(redis.getHost(), redis.getPort());
        standalone.setPassword(RedisPassword.of(redis.getPassword() == null ? "" : redis.getPassword()));
        ClientResources resources = ClientResources.builder()
                .tracing(LettuceTelemetry.create(openTelemetry).createTracing())
                .build();
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .clientResources(resources)
                .useSsl()
                .build();
        return new LettuceConnectionFactory(standalone, clientConfiguration);
    }

    @Bean
    @ConditionalOnProperty(prefix = "receipt.redis", name = "enabled", havingValue = "true")
    StringRedisTemplate receiptStringRedisTemplate(LettuceConnectionFactory receiptRedisConnectionFactory) {
        return new StringRedisTemplate(receiptRedisConnectionFactory);
    }

    @Bean
    @ConditionalOnProperty(prefix = "receipt.redis", name = "enabled", havingValue = "true")
    ReceiptCache receiptCache(StringRedisTemplate receiptStringRedisTemplate) {
        return new RedisReceiptCache(receiptStringRedisTemplate, receiptJson());
    }

    @Bean
    @ConditionalOnProperty(prefix = "receipt.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
    ReceiptCache disabledReceiptCache() {
        return new DisabledReceiptCache();
    }

    @Bean
    OpenTelemetry openTelemetry(Environment environment) {
        String connectionString = environment.getProperty("applicationinsights.connection.string", "");
        if (connectionString.isBlank()) {
            return OpenTelemetry.noop();
        }
        AutoConfiguredOpenTelemetrySdkBuilder builder = AutoConfiguredOpenTelemetrySdk.builder();
        AzureMonitorAutoConfigure.customize(builder, connectionString);
        OpenTelemetry sdk = builder.setResultAsGlobal().build().getOpenTelemetrySdk();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger root = context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        if (root.getAppender("otel") == null) {
            OpenTelemetryAppender appender = new OpenTelemetryAppender();
            appender.setContext(context);
            appender.setName("otel");
            appender.setCaptureMdcAttributes("*");
            appender.start();
            root.addAppender(appender);
        }
        OpenTelemetryAppender.install(sdk);
        return sdk;
    }

    @Bean
    ReceiptTelemetry receiptTelemetry(OpenTelemetry openTelemetry, Environment environment) {
        String connectionString = environment.getProperty("applicationinsights.connection.string", "");
        if (connectionString.isBlank()) {
            return new NoopReceiptTelemetry();
        }
        return new OpenTelemetryReceiptTelemetry(openTelemetry);
    }

    static BlobServiceClientBuilder blobServiceClientBuilder(String connectionString) {
        return new BlobServiceClientBuilder()
                .connectionString(connectionString);
    }

    static TableServiceClientBuilder tableServiceClientBuilder(String connectionString) {
        return new TableServiceClientBuilder()
                .connectionString(connectionString)
                .addPolicy(new CorrelationHeaderPolicy());
    }

    static QueueServiceClientBuilder queueServiceClientBuilder(String connectionString) {
        return new QueueServiceClientBuilder()
                .connectionString(connectionString)
                .addPolicy(new CorrelationHeaderPolicy());
    }

    private static String storageConnectionString(ReceiptProperties properties) {
        String connectionString = properties.getStorage().getConnectionString();
        if (connectionString == null || connectionString.isBlank()) {
            throw new IllegalStateException("AZURE_STORAGE_CONNECTION_STRING is required");
        }
        return connectionString;
    }

    private static ObjectMapper receiptJson() {
        return JsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    private static final class DisabledReceiptCache implements ReceiptCache {
        @Override
        public Optional<ReceiptEntity> find(String id) {
            throw disabled();
        }

        @Override
        public Optional<List<ReceiptEntity>> recent() {
            throw disabled();
        }

        @Override
        public void putProcessed(ReceiptEntity entity) {
            throw disabled();
        }

        private static IllegalStateException disabled() {
            return new IllegalStateException("Redis is disabled");
        }
    }
}
