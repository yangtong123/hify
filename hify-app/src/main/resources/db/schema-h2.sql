-- =============================================================
-- Hify 业务表 DDL (H2 兼容版，用于 mock profile)
-- 与原版差异: JSON/MEDIUMTEXT → CLOB, TINYINT → INTEGER
--           去掉 ENGINE/CHARSET/COLLATE/COMMENT
--           去掉 ON UPDATE (H2 不支持)
-- =============================================================

-- ----------------------------
-- 1. LLM 提供商
-- ----------------------------
CREATE TABLE t_provider (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(30) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    auth_config CLOB,
    enabled INTEGER DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

CREATE TABLE t_model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    model_id VARCHAR(100) NOT NULL,
    context_size INT,
    extra_params CLOB,
    enabled INTEGER DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

CREATE TABLE t_provider_health (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'UNKNOWN',
    last_check_at TIMESTAMP,
    last_success_at TIMESTAMP,
    fail_count INT DEFAULT 0,
    latency_ms INT,
    error_message VARCHAR(500),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX idx_provider_health_provider_id ON t_provider_health(provider_id);

-- ----------------------------
-- 4. Agent 配置
-- ----------------------------
CREATE TABLE t_agent (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100)    NOT NULL,
    description     VARCHAR(500)    NOT NULL DEFAULT '',
    system_prompt   CLOB,
    model_config_id BIGINT          NOT NULL,
    temperature     DECIMAL(3,2)    NOT NULL DEFAULT 0.70,
    max_tokens      INT             NOT NULL DEFAULT 4096,
    top_p           DECIMAL(3,2)    NOT NULL DEFAULT 1.00,
    max_context_turns INT           NOT NULL DEFAULT 10,
    config_json     CLOB,
    enabled         INTEGER         NOT NULL DEFAULT 1,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_agent_name_deleted ON t_agent(name, deleted);
CREATE INDEX idx_agent_model_deleted ON t_agent(model_config_id, deleted);

-- ----------------------------
-- 5. Agent 与 MCP Server 关联
-- ----------------------------
CREATE TABLE t_agent_mcp (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    agent_id        BIGINT          NOT NULL,
    mcp_server_id   BIGINT          NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_agent_mcp_server_deleted ON t_agent_mcp(agent_id, mcp_server_id, deleted);
CREATE INDEX idx_agent_mcp_agent_deleted ON t_agent_mcp(agent_id, deleted);

-- ----------------------------
-- 6. MCP 服务配置
-- ----------------------------
CREATE TABLE t_mcp_server (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100)    NOT NULL,
    description     VARCHAR(500),
    server_type     VARCHAR(50)     NOT NULL,
    command         VARCHAR(500),
    args            VARCHAR(1000),
    url             VARCHAR(500),
    api_key         VARCHAR(500),
    is_enabled      INTEGER         NOT NULL DEFAULT 1,
    config_json     CLOB,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_name_deleted_mcp ON t_mcp_server(name, deleted);
CREATE INDEX idx_type_enabled ON t_mcp_server(server_type, is_enabled, deleted);

-- Mock profile seed data for API verification.
INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, created_at, updated_at, deleted)
VALUES (1, 'Mock Provider', 'mock', 'http://mock.local', NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO t_model_config (id, provider_id, name, model_id, context_size, extra_params, enabled, created_at, updated_at, deleted)
VALUES (1, 1, 'Mock GPT', 'gpt-mock', 128000, NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO t_mcp_server (id, name, description, server_type, command, args, url, api_key, is_enabled, config_json, created_at, updated_at, deleted)
VALUES (1, 'Mock MCP', 'Mock MCP server for Agent verification', 'stdio', 'echo', NULL, NULL, NULL, 1, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- ----------------------------
-- 7. 对话会话
-- ----------------------------
CREATE TABLE t_chat_session (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    agent_id        BIGINT          NOT NULL,
    title           VARCHAR(200),
    user_id         VARCHAR(100)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_agent_deleted_created ON t_chat_session(agent_id, deleted, created_at);
CREATE INDEX idx_user_deleted_created ON t_chat_session(user_id, deleted, created_at);

-- ----------------------------
-- 8. 对话消息
-- ----------------------------
CREATE TABLE t_chat_message (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    session_id      BIGINT          NOT NULL,
    role            VARCHAR(20)     NOT NULL,
    content         CLOB,
    token_count     INT,
    tool_calls      CLOB,
    metadata        CLOB,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_session_cursor ON t_chat_message(session_id, deleted, created_at, id);

-- ----------------------------
-- 9. 知识库
-- ----------------------------
CREATE TABLE t_knowledge_base (
    id                        BIGINT          NOT NULL AUTO_INCREMENT,
    name                      VARCHAR(100)    NOT NULL,
    description               VARCHAR(500)    NOT NULL DEFAULT '',
    embedding_model_config_id BIGINT          NOT NULL,
    embedding_dimension       INT             NOT NULL,
    chunk_size                INT             NOT NULL DEFAULT 1000,
    chunk_overlap             INT             NOT NULL DEFAULT 150,
    top_k                     INT             NOT NULL DEFAULT 5,
    similarity_threshold      DECIMAL(6,4)    NOT NULL DEFAULT 0.7000,
    status                    VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
    created_at                TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                   INTEGER         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_knowledge_base_name_deleted ON t_knowledge_base(name, deleted);
CREATE INDEX idx_knowledge_base_status_deleted ON t_knowledge_base(status, deleted);

-- ----------------------------
-- 10. 知识库文档
-- ----------------------------
CREATE TABLE t_knowledge_document (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    knowledge_base_id BIGINT          NOT NULL,
    file_name         VARCHAR(255)    NOT NULL,
    file_type         VARCHAR(30)     NOT NULL,
    file_size         BIGINT          NOT NULL,
    storage_path      VARCHAR(500)    NOT NULL,
    content_hash      VARCHAR(64)     NOT NULL,
    title             VARCHAR(255),
    process_status    VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    chunk_count       INT             NOT NULL DEFAULT 0,
    error_message     VARCHAR(1000),
    processed_at      TIMESTAMP,
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           INTEGER         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_knowledge_document_hash_deleted ON t_knowledge_document(knowledge_base_id, content_hash, deleted);
CREATE INDEX idx_knowledge_document_status_deleted ON t_knowledge_document(knowledge_base_id, process_status, deleted);
CREATE INDEX idx_knowledge_document_created ON t_knowledge_document(knowledge_base_id, deleted, created_at);

-- ----------------------------
-- 11. Agent 与知识库关联
-- ----------------------------
CREATE TABLE t_agent_knowledge_base (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    agent_id          BIGINT          NOT NULL,
    knowledge_base_id BIGINT          NOT NULL,
    priority          INT             NOT NULL DEFAULT 100,
    enabled           INTEGER         NOT NULL DEFAULT 1,
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           INTEGER         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_agent_knowledge_base_deleted ON t_agent_knowledge_base(agent_id, knowledge_base_id, deleted);
CREATE INDEX idx_agent_knowledge_base_agent ON t_agent_knowledge_base(agent_id, enabled, deleted);
CREATE INDEX idx_agent_knowledge_base_kb ON t_agent_knowledge_base(knowledge_base_id, deleted);

-- ----------------------------
-- 12. 工作流
-- ----------------------------
CREATE TABLE t_workflow (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(128)    NOT NULL,
    description     VARCHAR(512)    NOT NULL DEFAULT '',
    status          VARCHAR(32)     NOT NULL DEFAULT 'draft',
    version         INT             NOT NULL DEFAULT 1,
    start_node_id   VARCHAR(64)     NOT NULL,
    config_json     CLOB,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_workflow_status_deleted ON t_workflow(status, deleted, created_at);
CREATE INDEX idx_workflow_created ON t_workflow(deleted, created_at);

CREATE TABLE t_workflow_node (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    workflow_id     BIGINT          NOT NULL,
    node_id         VARCHAR(64)     NOT NULL,
    node_type       VARCHAR(32)     NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    config_json     CLOB,
    position_json   CLOB,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_workflow_node ON t_workflow_node(workflow_id, deleted);
CREATE INDEX idx_workflow_node_type ON t_workflow_node(workflow_id, node_type, deleted);

CREATE TABLE t_workflow_edge (
    id                   BIGINT          NOT NULL AUTO_INCREMENT,
    workflow_id          BIGINT          NOT NULL,
    source_node_id       VARCHAR(64)     NOT NULL,
    target_node_id       VARCHAR(64)     NOT NULL,
    edge_type            VARCHAR(32)     NOT NULL DEFAULT 'normal',
    condition_expression VARCHAR(1024),
    priority             INT             NOT NULL DEFAULT 0,
    created_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              INTEGER         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_workflow_edge_source ON t_workflow_edge(workflow_id, source_node_id, deleted, priority);
CREATE INDEX idx_workflow_edge_target ON t_workflow_edge(workflow_id, target_node_id, deleted);

-- ----------------------------
-- 13. Demo 演示
-- ----------------------------
CREATE TABLE t_demo_item (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100)    NOT NULL,
    status          INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_name_deleted_demo ON t_demo_item(name, deleted);
