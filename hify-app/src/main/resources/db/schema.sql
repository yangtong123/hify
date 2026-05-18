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
    workflow_id     BIGINT          DEFAULT NULL COMMENT '绑定的工作流 ID',
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
    INDEX idx_agent_model_deleted (model_config_id, deleted),
    INDEX idx_agent_workflow_deleted (workflow_id, deleted)
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
    INDEX idx_agent_mcp_agent_deleted (agent_id, deleted),
    INDEX idx_agent_mcp_server_deleted (mcp_server_id, deleted)
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
-- 7. MCP 工具缓存
-- ----------------------------
CREATE TABLE t_mcp_tool (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    mcp_server_id   BIGINT          NOT NULL COMMENT 'MCP Server ID',
    name            VARCHAR(100)    NOT NULL COMMENT '工具名称',
    description     VARCHAR(1000)   COMMENT '工具描述',
    input_schema    JSON            COMMENT '工具参数 JSON Schema',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE INDEX uk_mcp_tool_name_deleted (mcp_server_id, name, deleted),
    INDEX idx_mcp_tool_server_deleted (mcp_server_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 工具缓存';

-- ----------------------------
-- 8. 对话会话
-- ----------------------------
CREATE TABLE t_chat_session (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    agent_id        BIGINT          DEFAULT NULL COMMENT '关联 Agent',
    workflow_id     BIGINT          DEFAULT NULL COMMENT '关联工作流',
    title           VARCHAR(200)    COMMENT '会话标题（首轮对话自动生成）',
    user_id         VARCHAR(100)    NOT NULL COMMENT '用户标识',
    status          VARCHAR(20)     NOT NULL DEFAULT 'active' COMMENT 'active / archived',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_agent_deleted_created (agent_id, deleted, created_at),
    INDEX idx_workflow_deleted_created (workflow_id, deleted, created_at),
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
-- 9. 知识库
-- ----------------------------
CREATE TABLE t_knowledge_base (
    id                        BIGINT          NOT NULL AUTO_INCREMENT,
    name                      VARCHAR(100)    NOT NULL COMMENT '知识库名称',
    description               VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '知识库描述',
    embedding_model_config_id BIGINT          NOT NULL COMMENT 'Embedding 模型配置 ID',
    embedding_dimension       INT             NOT NULL COMMENT '向量维度',
    chunk_size                INT             NOT NULL DEFAULT 1000 COMMENT '分块大小',
    chunk_overlap             INT             NOT NULL DEFAULT 150 COMMENT '分块重叠长度',
    top_k                     INT             NOT NULL DEFAULT 5 COMMENT '默认召回数量',
    similarity_threshold      DECIMAL(6,4)    NOT NULL DEFAULT 0.7000 COMMENT '相似度阈值',
    status                    VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_at                DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at                DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted                   TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE INDEX uk_knowledge_base_name_deleted (name, deleted),
    INDEX idx_knowledge_base_status_deleted (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库';

-- ----------------------------
-- 10. 知识库文档
-- ----------------------------
CREATE TABLE t_knowledge_document (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    knowledge_base_id BIGINT          NOT NULL COMMENT '知识库 ID',
    file_name         VARCHAR(255)    NOT NULL COMMENT '原始文件名',
    file_type         VARCHAR(30)     NOT NULL COMMENT '文件类型',
    file_size         BIGINT          NOT NULL COMMENT '文件大小',
    storage_path      VARCHAR(500)    NOT NULL COMMENT '原始文件存储路径',
    content_hash      VARCHAR(64)     NOT NULL COMMENT '内容 hash',
    title             VARCHAR(255)    COMMENT '文档标题',
    process_status    VARCHAR(30)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PARSING/CHUNKING/EMBEDDING/COMPLETED/FAILED',
    chunk_count       INT             NOT NULL DEFAULT 0 COMMENT '分块数量',
    error_message     VARCHAR(1000)   COMMENT '失败原因',
    processed_at      DATETIME(3)     COMMENT '处理完成时间',
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted           TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE INDEX uk_knowledge_document_hash_deleted (knowledge_base_id, content_hash, deleted),
    INDEX idx_knowledge_document_status_deleted (knowledge_base_id, process_status, deleted),
    INDEX idx_knowledge_document_created (knowledge_base_id, deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档';

-- ----------------------------
-- 11. Agent 与知识库关联
-- ----------------------------
CREATE TABLE t_agent_knowledge_base (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    agent_id          BIGINT          NOT NULL COMMENT 'Agent ID',
    knowledge_base_id BIGINT          NOT NULL COMMENT '知识库 ID',
    priority          INT             NOT NULL DEFAULT 100 COMMENT '优先级，数字越小越优先',
    enabled           TINYINT(1)      NOT NULL DEFAULT 1,
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted           TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE INDEX uk_agent_knowledge_base_deleted (agent_id, knowledge_base_id, deleted),
    INDEX idx_agent_knowledge_base_agent (agent_id, enabled, deleted),
    INDEX idx_agent_knowledge_base_kb (knowledge_base_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 与知识库关联';

-- PostgreSQL + pgvector 表由 KnowledgeVectorRepository 在首次写入或检索时创建：
-- CREATE EXTENSION IF NOT EXISTS vector;
-- CREATE TABLE knowledge_chunk (... embedding vector(1024) ...);

-- ----------------------------
-- 12. 工作流
-- ----------------------------
CREATE TABLE t_workflow (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(128)    NOT NULL COMMENT '工作流名称',
    description     VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '工作流描述',
    status          VARCHAR(32)     NOT NULL DEFAULT 'draft' COMMENT 'draft/published/disabled',
    version         INT             NOT NULL DEFAULT 1 COMMENT '版本号',
    start_node_id   VARCHAR(64)     NOT NULL COMMENT '开始节点业务 ID',
    config_json     JSON            COMMENT '全局配置',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_workflow_status_deleted (status, deleted, created_at),
    INDEX idx_workflow_created (deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流定义';

CREATE TABLE t_workflow_node (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    workflow_id     BIGINT          NOT NULL COMMENT '工作流 ID',
    node_id         VARCHAR(64)     NOT NULL COMMENT '节点业务 ID',
    node_type       VARCHAR(32)     NOT NULL COMMENT 'start/llm/knowledge/condition/tool/end',
    name            VARCHAR(128)    NOT NULL COMMENT '节点名称',
    config_json     JSON            COMMENT '节点类型专属配置',
    position_json   JSON            COMMENT '前端画布位置',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_workflow_node (workflow_id, deleted),
    INDEX idx_workflow_node_type (workflow_id, node_type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流节点';

CREATE TABLE t_workflow_edge (
    id                   BIGINT          NOT NULL AUTO_INCREMENT,
    workflow_id          BIGINT          NOT NULL COMMENT '工作流 ID',
    source_node_id       VARCHAR(64)     NOT NULL COMMENT '起点节点业务 ID',
    target_node_id       VARCHAR(64)     NOT NULL COMMENT '终点节点业务 ID',
    edge_type            VARCHAR(32)     NOT NULL DEFAULT 'normal' COMMENT 'normal/condition/error',
    condition_expression VARCHAR(1024)   COMMENT '条件表达式',
    priority             INT             NOT NULL DEFAULT 0 COMMENT '匹配顺序，越小越优先',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted              TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_workflow_edge_source (workflow_id, source_node_id, deleted, priority),
    INDEX idx_workflow_edge_target (workflow_id, target_node_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流节点连接';

CREATE TABLE t_workflow_run (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    workflow_id      BIGINT          NOT NULL COMMENT '工作流 ID',
    workflow_version INT             NOT NULL DEFAULT 1 COMMENT '执行时工作流版本',
    user_id          VARCHAR(100)    NOT NULL COMMENT '用户标识',
    status           VARCHAR(32)     NOT NULL COMMENT 'running/succeeded/failed',
    inputs_json      JSON            COMMENT '执行输入',
    outputs_json     JSON            COMMENT '执行输出',
    error_message    VARCHAR(1000)   COMMENT '失败原因',
    started_at       DATETIME(3)     NOT NULL COMMENT '开始时间',
    finished_at      DATETIME(3)     COMMENT '结束时间',
    elapsed_ms       BIGINT          COMMENT '耗时 ms',
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted          TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_workflow_run_workflow (workflow_id, deleted, created_at),
    INDEX idx_workflow_run_user (user_id, deleted, created_at),
    INDEX idx_workflow_run_status (status, deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流执行记录';

CREATE TABLE t_workflow_node_run (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    workflow_run_id  BIGINT          NOT NULL COMMENT '工作流执行 ID',
    workflow_id      BIGINT          NOT NULL COMMENT '工作流 ID',
    node_id          VARCHAR(64)     NOT NULL COMMENT '节点业务 ID',
    node_type        VARCHAR(32)     NOT NULL COMMENT '节点类型',
    status           VARCHAR(32)     NOT NULL COMMENT 'succeeded/failed/skipped',
    input_json       JSON            COMMENT '节点输入快照',
    output_json      JSON            COMMENT '节点输出',
    error_message    VARCHAR(1000)   COMMENT '失败原因',
    started_at       DATETIME(3)     NOT NULL COMMENT '开始时间',
    finished_at      DATETIME(3)     COMMENT '结束时间',
    elapsed_ms       BIGINT          COMMENT '耗时 ms',
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted          TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_workflow_node_run (workflow_run_id, deleted, id),
    INDEX idx_workflow_node_run_node (workflow_id, node_id, deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流节点执行记录';

-- ----------------------------
-- 13. Demo 演示
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
