package com.hify.modules.knowledge.domain;

import com.hify.common.config.CacheConfig;
import com.hify.common.config.RedisConfig;
import com.hify.common.util.RedisUtil;
import com.hify.modules.knowledge.api.dto.RetrievedChunkDto;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest(
        classes = KnowledgeServiceImplTest.RealDatabaseTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "hify.test.real-rag.enabled", matches = "true")
class KnowledgeServiceImplTest {

    @Autowired
    private KnowledgeServiceImpl knowledgeService;

    @DynamicPropertySource
    static void realDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> propertyOrEnv(
                "hify.test.mysql-url",
                "MYSQL_JDBC_URL",
                "jdbc:mysql://localhost:3306/hify?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&serverTimezone=Asia/Shanghai"));
        registry.add("spring.datasource.username", () -> propertyOrEnv("hify.test.mysql-user", "MYSQL_USER", "root"));
        registry.add("spring.datasource.password", () -> propertyOrEnv("hify.test.mysql-password", "MYSQL_PASSWORD", "123456"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.autoconfigure.exclude", () -> String.join(",",
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"));

        registry.add("hify.knowledge.vector.jdbc-url", () -> propertyOrEnv(
                "hify.test.pgvector-url",
                "PGVECTOR_JDBC_URL",
                "jdbc:postgresql://localhost:5432/hify_vector"));
        registry.add("hify.knowledge.vector.username", () -> propertyOrEnv("hify.test.pgvector-user", "PGVECTOR_USER", "postgres"));
        registry.add("hify.knowledge.vector.password", () -> propertyOrEnv("hify.test.pgvector-password", "PGVECTOR_PASSWORD", "123456"));
    }

    @Test
    void retrieveForAgentShouldMatchChunksFromRealDatabaseAndEmbeddingService() {
        Long agentId = Long.getLong("hify.test.rag.agent-id", 5L);
        String query = System.getProperty("hify.test.rag.query", "你们支持七天无理由退货吗？已拆封的怎么办");

        List<RetrievedChunkDto> chunks = knowledgeService.retrieveForAgent(agentId, query);

        assertThat(chunks)
                .as("agentId=%s, query=%s 应该能从真实知识库召回 chunk", agentId, query)
                .isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getKnowledgeBaseId()).isNotNull();
            assertThat(chunk.getDocumentId()).isNotNull();
            assertThat(chunk.getContent()).isNotBlank();
            assertThat(chunk.getSimilarity()).isNotNull();
        });

        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunkDto chunk = chunks.get(i);
            log.info("Real RAG hit #{}: similarity={}, documentId={}, fileName={}, content={}",
                    i + 1,
                    chunk.getSimilarity(),
                    chunk.getDocumentId(),
                    chunk.getFileName(),
                    abbreviate(chunk.getContent(), 160));
        }
    }

    private static String propertyOrEnv(String propertyName, String envName, String defaultValue) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return defaultValue;
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(
            basePackages = "com.hify",
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {RedisConfig.class, CacheConfig.class, RedisUtil.class}))
    @MapperScan("com.hify.**.mapper")
    static class RealDatabaseTestApplication {
    }
}
