-- Patch: align existing MySQL databases with current Agent schema.
-- Symptom: creating an Agent fails with "Unknown column 'max_context_turns' in 'field list'".
--
-- Run this once against the real hify database if it was created from an older schema.

ALTER TABLE t_agent
    ADD COLUMN max_context_turns INT NOT NULL DEFAULT 10 COMMENT '保留最近 N 轮上下文'
    AFTER top_p;

