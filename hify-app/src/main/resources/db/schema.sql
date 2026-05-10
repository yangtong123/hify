-- =============================================================
-- Hify 业务表 DDL
-- 字符集: utf8mb4  排序规则: utf8mb4_unicode_ci  引擎: InnoDB
-- 每表必备: id / created_at / updated_at / deleted
-- =============================================================

-- ----------------------------
-- 1. LLM 提供商
-- ----------------------------
CREATE TABLE t_provider (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100)    NOT NULL COMMENT '提供商名称',
    provider_type   VARCHAR(50)     NOT NULL COMMENT '提供商类型: openai/claude/gemini/ollama',
    api_base_url    VARCHAR(500)    NOT NULL COMMENT 'API 根地址',
    api_key         VARCHAR(500)    NOT NULL COMMENT 'API 密钥（加密存储）',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    config_json     JSON            COMMENT '扩展配置: 自定义 header、超时覆盖等',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE INDEX idx_name_deleted (name, deleted),
    INDEX idx_type_enabled (provider_type, is_enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM 提供商';

-- ----------------------------
-- 2. 模型配置（关联提供商）
-- ----------------------------
CREATE TABLE t_model_config (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    provider_id         BIGINT          NOT NULL COMMENT '所属提供商',
    model_name          VARCHAR(100)    NOT NULL COMMENT '模型标识: gpt-4o / claude-sonnet-4-6 / gemini-2.5-pro',
    display_name        VARCHAR(200)    COMMENT '前端展示名称',
    context_window      INT             COMMENT '上下文窗口大小（token 上限）',
    max_tokens          INT             COMMENT '最大输出 token',
    supports_streaming  TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否支持流式 SSE',
    supports_vision     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否支持图片输入',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    config_json         JSON            COMMENT '扩展参数: temperature 默认值、top_p 默认值等',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_provider_deleted (provider_id, deleted),
    UNIQUE INDEX idx_provider_model (provider_id, model_name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型配置';

-- ----------------------------
-- 3. Agent 配置
-- ----------------------------
CREATE TABLE t_agent (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100)    NOT NULL COMMENT 'Agent 名称',
    description     VARCHAR(500)    COMMENT 'Agent 描述',
    system_prompt   MEDIUMTEXT      COMMENT '系统提示词',
    model_config_id BIGINT          NOT NULL COMMENT '绑定的模型',
    temperature     DECIMAL(3,2)    DEFAULT 0.70 COMMENT '温度参数',
    max_tokens      INT             DEFAULT 4096 COMMENT '最大输出 token',
    top_p           DECIMAL(3,2)    DEFAULT 1.00 COMMENT '核采样参数',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
    config_json     JSON            COMMENT '扩展配置: 知识库检索阈值、工具调用策略等',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_name_deleted (name, deleted),
    INDEX idx_model_deleted (model_config_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 配置';

-- ----------------------------
-- 4. Agent 与 MCP 工具关联
-- ----------------------------
CREATE TABLE t_agent_tool (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    agent_id        BIGINT          NOT NULL COMMENT 'Agent ID',
    mcp_server_id   BIGINT          NOT NULL COMMENT 'MCP 服务 ID',
    tool_name       VARCHAR(200)    NOT NULL COMMENT '工具名称',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_agent_deleted (agent_id, deleted),
    UNIQUE INDEX idx_agent_server_tool (agent_id, mcp_server_id, tool_name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 关联 MCP 工具';

-- ----------------------------
-- 5. MCP 服务配置
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
-- 6. 对话会话
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
-- 7. 对话消息（游标分页）
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
-- 8. Demo 演示
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
