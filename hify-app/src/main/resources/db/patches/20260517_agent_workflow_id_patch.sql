-- Patch: bind Agent to an optional Workflow.
--
-- Project table name is t_agent.
-- Run this once against existing MySQL databases.

ALTER TABLE t_agent
    ADD COLUMN workflow_id BIGINT DEFAULT NULL COMMENT '绑定的工作流 ID'
    AFTER model_config_id;

CREATE INDEX idx_agent_workflow_deleted ON t_agent(workflow_id, deleted);
