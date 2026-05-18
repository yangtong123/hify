-- Add MCP tool cache table populated from tools/list during MCP Server tests.
CREATE INDEX idx_agent_mcp_server_deleted ON t_agent_mcp(mcp_server_id, deleted);

CREATE TABLE IF NOT EXISTS t_mcp_tool (
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
