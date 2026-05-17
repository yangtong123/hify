ALTER TABLE t_chat_session
    MODIFY COLUMN agent_id BIGINT DEFAULT NULL COMMENT '关联 Agent',
    ADD COLUMN workflow_id BIGINT DEFAULT NULL COMMENT '关联工作流' AFTER agent_id;

CREATE INDEX idx_workflow_deleted_created
    ON t_chat_session(workflow_id, deleted, created_at);
