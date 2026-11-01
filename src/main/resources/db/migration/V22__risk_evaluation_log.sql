-- ============================================================================
-- V22__risk_evaluation_log.sql  ② 号段：评估留痕（可解释性 + 风险拦截统计口径的唯一来源）
-- input_snapshot 只固化参与判定的最小变量（成分码/剂量/标签码），绝不写病历原文
-- 这张表是报表模块里"风险拦截统计"的数据源，报表侧只读、不得回写
-- ============================================================================

CREATE TABLE IF NOT EXISTS risk_evaluation_log (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id          VARCHAR(32)   NOT NULL,
    family_id         BIGINT,
    member_id         BIGINT,
    /* 本次求值所用规则集指纹：publishedAt + 规则条数，命中后可回溯当时的规则库 */
    rule_set_stamp    VARCHAR(64)   NOT NULL,
    scenario          VARCHAR(24)   NOT NULL,
    hit_count         INT           NOT NULL DEFAULT 0,
    max_severity      VARCHAR(8),
    input_snapshot    TEXT          NOT NULL,
    hits_json         TEXT,
    /* 求值出错的规则（表达式异常/变量缺失），单独留痕，不能静默当"无风险" */
    failed_rule_count INT           NOT NULL DEFAULT 0,
    evaluated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_risk_eval_trace ON risk_evaluation_log (trace_id, scenario);
CREATE INDEX idx_risk_eval_member_time ON risk_evaluation_log (member_id, evaluated_at);
CREATE INDEX idx_risk_eval_family_time ON risk_evaluation_log (family_id, evaluated_at);
