package com.hify.modules.knowledge.infra.vector;

import com.hify.modules.knowledge.infra.config.KnowledgeVectorProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class KnowledgeVectorRepository {

    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeVectorProperties properties;

    public KnowledgeVectorRepository(@Qualifier("knowledgeVectorJdbcTemplate") JdbcTemplate jdbcTemplate,
                                     KnowledgeVectorProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public void ensureSchema(int embeddingDimension) {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS knowledge_chunk (
                    id BIGSERIAL PRIMARY KEY,
                    knowledge_base_id BIGINT NOT NULL,
                    document_id BIGINT NOT NULL,
                    chunk_index INT NOT NULL,
                    content TEXT NOT NULL,
                    token_count INT NOT NULL DEFAULT 0,
                    char_count INT NOT NULL DEFAULT 0,
                    page_number INT,
                    section_title VARCHAR(255),
                    embedding vector(%d) NOT NULL,
                    embedding_model_config_id BIGINT NOT NULL,
                    embedding_model VARCHAR(100) NOT NULL,
                    content_hash VARCHAR(64) NOT NULL,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE,
                    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """.formatted(embeddingDimension));
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_chunk_doc_index
                ON knowledge_chunk (document_id, chunk_index)
                WHERE deleted = FALSE
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_kb_enabled
                ON knowledge_chunk (knowledge_base_id, enabled, deleted)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_embedding_ivfflat
                ON knowledge_chunk
                USING ivfflat (embedding vector_cosine_ops)
                WITH (lists = %d)
                """.formatted(properties.getIvfflatLists()));
    }

    public void replaceDocumentChunks(Long documentId, List<KnowledgeChunkRecord> chunks, int embeddingDimension) {
        ensureSchema(embeddingDimension);
        jdbcTemplate.update("UPDATE knowledge_chunk SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE document_id = ?", documentId);
        for (KnowledgeChunkRecord chunk : chunks) {
            jdbcTemplate.update("""
                    INSERT INTO knowledge_chunk (
                        knowledge_base_id, document_id, chunk_index, content, token_count, char_count,
                        page_number, section_title, embedding, embedding_model_config_id, embedding_model, content_hash
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::vector, ?, ?, ?)
                    """,
                    chunk.getKnowledgeBaseId(),
                    chunk.getDocumentId(),
                    chunk.getChunkIndex(),
                    chunk.getContent(),
                    chunk.getTokenCount(),
                    chunk.getCharCount(),
                    chunk.getPageNumber(),
                    chunk.getSectionTitle(),
                    toVectorLiteral(chunk.getEmbedding()),
                    chunk.getEmbeddingModelConfigId(),
                    chunk.getEmbeddingModel(),
                    chunk.getContentHash());
        }
    }

    public List<KnowledgeChunkRecord> search(List<Long> knowledgeBaseIds,
                                             List<Double> queryEmbedding,
                                             int topK,
                                             double similarityThreshold) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return List.of();
        }
        jdbcTemplate.execute("SET ivfflat.probes = " + properties.getProbes());
        String placeholders = String.join(",", knowledgeBaseIds.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(toVectorLiteral(queryEmbedding));
        args.addAll(knowledgeBaseIds);
        args.add(toVectorLiteral(queryEmbedding));
        args.add(topK);

        List<KnowledgeChunkRecord> records = jdbcTemplate.query("""
                        SELECT id, knowledge_base_id, document_id, chunk_index, content, token_count, char_count,
                               page_number, section_title, embedding_model_config_id, embedding_model, content_hash,
                               embedding <=> ?::vector AS distance, created_at
                        FROM knowledge_chunk
                        WHERE knowledge_base_id IN (%s)
                          AND enabled = TRUE
                          AND deleted = FALSE
                        ORDER BY embedding <=> ?::vector
                        LIMIT ?
                        """.formatted(placeholders),
                this::mapRecord,
                args.toArray());
        return records.stream()
                .filter(record -> 1.0D - record.getDistance() >= similarityThreshold)
                .toList();
    }

    public List<KnowledgeChunkRecord> listDocumentChunks(Long documentId) {
        return jdbcTemplate.query("""
                        SELECT id, knowledge_base_id, document_id, chunk_index, content, token_count, char_count,
                               page_number, section_title, embedding_model_config_id, embedding_model, content_hash,
                               NULL::DOUBLE PRECISION AS distance, created_at
                        FROM knowledge_chunk
                        WHERE document_id = ?
                          AND enabled = TRUE
                          AND deleted = FALSE
                        ORDER BY chunk_index ASC
                        """,
                this::mapRecord,
                documentId);
    }

    public void deleteDocumentChunks(Long documentId) {
        jdbcTemplate.update("UPDATE knowledge_chunk SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE document_id = ?", documentId);
    }

    private KnowledgeChunkRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        KnowledgeChunkRecord record = new KnowledgeChunkRecord();
        record.setId(rs.getLong("id"));
        record.setKnowledgeBaseId(rs.getLong("knowledge_base_id"));
        record.setDocumentId(rs.getLong("document_id"));
        record.setChunkIndex(rs.getInt("chunk_index"));
        record.setContent(rs.getString("content"));
        record.setTokenCount(rs.getInt("token_count"));
        record.setCharCount(rs.getInt("char_count"));
        int pageNumber = rs.getInt("page_number");
        record.setPageNumber(rs.wasNull() ? null : pageNumber);
        record.setSectionTitle(rs.getString("section_title"));
        record.setEmbeddingModelConfigId(rs.getLong("embedding_model_config_id"));
        record.setEmbeddingModel(rs.getString("embedding_model"));
        record.setContentHash(rs.getString("content_hash"));
        double distance = rs.getDouble("distance");
        record.setDistance(rs.wasNull() ? null : distance);
        record.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return record;
    }

    private String toVectorLiteral(List<Double> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values.get(i));
        }
        builder.append(']');
        return builder.toString();
    }
}
