package com.hammerly.ai.rag;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RagProperties.class)
@ConditionalOnProperty(prefix = "hammerly.ai.rag", name = "enabled", havingValue = "true")
public class RagDatabaseConfiguration {
    @Bean(destroyMethod = "close")
    DataSource ragDataSource(RagProperties properties) {
        if (!StringUtils.hasText(properties.datasourceUrl())) {
            throw new IllegalStateException("SUPABASE_DB_URL is required when HAMMERLY_RAG_ENABLED=true");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.datasourceUrl());
        config.setUsername(properties.datasourceUsername());
        config.setPassword(properties.datasourcePassword());
        config.setSchema("hammerly");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(Math.max(250, properties.timeout().toMillis()));
        config.addDataSourceProperty("sslmode", properties.datasourceSslMode());
        return new HikariDataSource(config);
    }

    @Bean
    JdbcTemplate ragJdbcTemplate(DataSource ragDataSource, RagProperties properties) {
        JdbcTemplate jdbc = new JdbcTemplate(ragDataSource);
        jdbc.setQueryTimeout((int) Math.max(1, properties.timeout().toSeconds()));
        return jdbc;
    }

    @Bean(destroyMethod = "close")
    ExecutorService ragExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
