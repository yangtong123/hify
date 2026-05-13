-- =============================================================
-- Hify 业务表 DDL
-- 字符集: utf8mb4  排序规则: utf8mb4_unicode_ci  引擎: InnoDB
-- 每表必备: id / created_at / updated_at / deleted
-- =============================================================

-- ----------------------------
-- 1. LLM 提供商
-- ----------------------------
-- 模型提供商
CREATE TABLE t_provider (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '供应商名称，唯一',
    type VARCHAR(30) NOT NULL COMMENT 'OPENAI/ANTHROPIC/OLLAMA/OPENAI_COMPATIBLE',
    base_url VARCHAR(500) NOT NULL COMMENT 'API 基础地址',
    auth_config JSON COMMENT '鉴权配置，结构按 type 不同',
    enabled TINYINT DEFAULT 1 COMMENT '0=禁用 1=启用',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted TINYINT DEFAULT 0
) COMMENT '模型提供商';

-- 模型配置
CREATE TABLE t_model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT NOT NULL COMMENT '所属供应商 ID',
    name VARCHAR(100) NOT NULL COMMENT '展示名，如 GPT-4o',
    model_id VARCHAR(100) NOT NULL COMMENT '调用时传给 API 的值',
    context_size INT COMMENT '上下文窗口大小（token 数）',
    extra_params JSON COMMENT '模型级别扩展参数',
    enabled TINYINT DEFAULT 1 COMMENT '0=禁用 1=启用',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted TINYINT DEFAULT 0
) COMMENT '模型配置';

-- 供应商健康状态（独立表，高频写不影响 provider 缓存）
CREATE TABLE t_provider_health (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT NOT NULL COMMENT '供应商 ID，唯一索引',
    status VARCHAR(20) DEFAULT 'UNKNOWN' COMMENT 'UP/DOWN/DEGRADED/UNKNOWN',
    last_check_at DATETIME COMMENT '最后探测时间',
    last_success_at DATETIME COMMENT '最后成功时间',
    fail_count INT DEFAULT 0 COMMENT '连续失败次数',
    latency_ms INT COMMENT '最近一次延迟 ms',
    error_message VARCHAR(500) COMMENT '最近失败原因',
    updated_at DATETIME NOT NULL,
    UNIQUE INDEX idx_provider_health_provider_id (provider_id)
) COMMENT '供应商健康状态';
-- ----------------------------
-- 4. Agent 配置
-- ----------------------------
CREATE TABLE t_agent (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100)    NOT NULL,
    description     VARCHAR(500)    NOT NULL DEFAULT '',
    system_prompt   MEDIUMTEXT      COMMENT '系统提示词',
    model_config_id BIGINT          NOT NULL COMMENT '绑定的模型配置 ID',
    temperature     DECIMAL(3,2)    NOT NULL DEFAULT 0.70 COMMENT '0.00~2.00',
    max_tokens      INT             NOT NULL DEFAULT 4096 COMMENT '最大输出 token 数',
    top_p           DECIMAL(3,2)    NOT NULL DEFAULT 1.00 COMMENT '核采样参数',
    max_context_turns INT           NOT NULL DEFAULT 10 COMMENT '保留最近 N 轮上下文',
    config_json     JSON            COMMENT '扩展配置：openingMessage、suggestedQuestions、maxIterations 等',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE INDEX uk_agent_name_deleted (name, deleted),
    INDEX idx_agent_model_deleted (model_config_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 配置';

-- ----------------------------
-- 5. Agent 与 MCP Server 关联
-- ----------------------------
CREATE TABLE t_agent_mcp (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    agent_id        BIGINT          NOT NULL COMMENT 'Agent ID',
    mcp_server_id   BIGINT          NOT NULL COMMENT 'MCP Server ID',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE INDEX uk_agent_mcp_server_deleted (agent_id, mcp_server_id, deleted),
    INDEX idx_agent_mcp_agent_deleted (agent_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 与 MCP Server 关联';

-- ----------------------------
-- 6. MCP 服务配置
-- ----------------------------
CREATE TABLE t_mcp_server (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100)    NOT NULL COMMENT '服务名称',
    description     VARCHAR(500)    COMMENT '服务描述',
    server_type     VARCHAR(50)     NOT NULL COMMENT '连接类型: stdio / sse / streamable_http',
    command         VARCHAR(500)    COMMENT 'stdio 类型: 启动命令',
    args            VARCHAR(1000)   COMMENT 'stdio 类型: 命令参数',
    url             VARCHAR(500)    COMMENT 'sse / http 类型: 服务 URL',
    api_key         VARCHAR(500)    COMMENT '认证密钥',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
    config_json     JSON            COMMENT '扩展配置: 环境变量、超时等',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_name_deleted (name, deleted),
    INDEX idx_type_enabled (server_type, is_enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 服务';

-- ----------------------------
-- 7. 对话会话
-- ----------------------------
CREATE TABLE t_chat_session (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    agent_id        BIGINT          NOT NULL COMMENT '关联 Agent',
    title           VARCHAR(200)    COMMENT '会话标题（首轮对话自动生成）',
    user_id         VARCHAR(100)    NOT NULL COMMENT '用户标识',
    status          VARCHAR(20)     NOT NULL DEFAULT 'active' COMMENT 'active / archived',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_agent_deleted_created (agent_id, deleted, created_at),
    INDEX idx_user_deleted_created (user_id, deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话';

-- ----------------------------
-- 8. 对话消息（游标分页）
-- ----------------------------
CREATE TABLE t_chat_message (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    session_id      BIGINT          NOT NULL COMMENT '所属会话',
    role            VARCHAR(20)     NOT NULL COMMENT '角色: user / assistant / system / tool',
    content         MEDIUMTEXT      COMMENT '消息内容',
    token_count     INT             COMMENT '消耗 token 数',
    tool_calls      JSON            COMMENT '工具调用记录',
    metadata        JSON            COMMENT '扩展元数据: 模型名、延迟等',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_session_cursor (session_id, deleted, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息';

-- ----------------------------
-- 9. Demo 演示
-- ----------------------------
CREATE TABLE t_demo_item (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100)    NOT NULL COMMENT '名称',
    status          INT             NOT NULL DEFAULT 0 COMMENT '状态',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_name_deleted (name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Demo 演示表';
