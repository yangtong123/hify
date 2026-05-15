package com.hify.modules.knowledge.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "hify.knowledge.vector")
public class KnowledgeVectorProperties {

    private String jdbcUrl = "jdbc:postgresql://localhost:5432/hify_vector";

    private String username = "postgres";

    private String password = "123456";

    private int maximumPoolSize = 5;

    private int connectionTimeoutMs = 3000;

    private int ivfflatLists = 100;

    private int probes = 10;
}
