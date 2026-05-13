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
-- 9. Demo 演示
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
