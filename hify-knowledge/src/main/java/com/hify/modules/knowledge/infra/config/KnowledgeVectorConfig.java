package com.hify.modules.knowledge.infra.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableConfigurationProperties(KnowledgeVectorProperties.class)
public class KnowledgeVectorConfig {

    @Bean("knowledgeVectorJdbcTemplate")
    public JdbcTemplate knowledgeVectorJdbcTemplate(KnowledgeVectorProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getJdbcUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setMaximumPoolSize(properties.getMaximumPoolSize());
        config.setConnectionTimeout(properties.getConnectionTimeoutMs());
        config.setPoolName("hify-knowledge-vector");
        return new JdbcTemplate(new HikariDataSource(config));
    }
}
