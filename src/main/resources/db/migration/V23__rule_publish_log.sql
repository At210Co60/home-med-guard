-- ============================================================================
-- V23__rule_publish_log.sql  ② 号段：规则流转留痕（草稿→提交→审核→发布→停用）
-- 审计口径：谁在什么时候把哪条规则的哪个版本从什么状态动到什么状态、理由是什么
-- ============================================================================

CREATE TABLE IF NOT EXISTS rule_publish_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_table     VARCHAR(32)  NOT NULL,
    rule_code      VARCHAR(64)  NOT NULL,
    rule_version   INT          NOT NULL,
    action         VARCHAR(16)  NOT NULL,
    actor_user_id  BIGINT       NOT NULL,
    reason         VARCHAR(512),
    snapshot_json  TEXT,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_rule_publish_log_rule ON rule_publish_log (rule_table, rule_code, rule_version);
