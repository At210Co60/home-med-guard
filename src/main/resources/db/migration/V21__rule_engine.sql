-- ============================================================================
-- V21__rule_engine.sql  ② 号段：数据驱动规则（行存 DB + Aviator 表达式 + 版本化）
-- 发布后不可原地改：只能新版本；评估结果固化命中的 rule_code + version
-- expr 为空表示"命中即触发"，非空时用上下文变量做附加条件（见 RuleEngine 的变量表）
-- ============================================================================

-- 药-药 / 药-食 / 药-酒：同一张表用 target_type 区分
-- severity：A=严重(建议停用或立即就医) B=慎用(需间隔或减量) C=注意(告知即可)
CREATE TABLE IF NOT EXISTS interaction_rule (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_code      VARCHAR(64)  NOT NULL,
    version        INT          NOT NULL DEFAULT 1,
    status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    effective_from DATE,
    /* DRUG / FOOD / ALCOHOL */
    target_type    VARCHAR(16)  NOT NULL DEFAULT 'DRUG',
    component_a    VARCHAR(64)  NOT NULL,
    /* 药品填成分码；食物/酒精填目标码（如 ALCOHOL、GRAPEFRUIT_JUICE） */
    component_b    VARCHAR(64)  NOT NULL,
    severity       VARCHAR(8)   NOT NULL,
    mechanism      VARCHAR(255),
    advice         VARCHAR(512) NOT NULL,
    expr           VARCHAR(512),
    /* 来源必须是说明书原文或指南条目，答辩与纠错都要能追到 */
    source_ref     VARCHAR(255) NOT NULL,
    source_date    DATE         NOT NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_interaction_rule UNIQUE (rule_code, version)
);
CREATE INDEX idx_interaction_a ON interaction_rule (component_a, status);
CREATE INDEX idx_interaction_b ON interaction_rule (component_b, status);

-- 禁忌人群：tag_code 来自 ① 的成员档案标签（PREGNANT / LACTATING / CHILD / ...）
CREATE TABLE IF NOT EXISTS contraindication_rule (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_code      VARCHAR(64)  NOT NULL,
    version        INT          NOT NULL DEFAULT 1,
    status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    effective_from DATE,
    component      VARCHAR(64)  NOT NULL,
    tag_code       VARCHAR(32)  NOT NULL,
    severity       VARCHAR(8)   NOT NULL,
    advice         VARCHAR(512) NOT NULL,
    expr           VARCHAR(512),
    source_ref     VARCHAR(255) NOT NULL,
    source_date    DATE         NOT NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_contraindication_rule UNIQUE (rule_code, version)
);
CREATE INDEX idx_contradication_comp ON contraindication_rule (component, status);

-- 服药时间打架：只给最小间隔分钟数，怎么错开由 ③ 的排程求解器决定
CREATE TABLE IF NOT EXISTS spacing_rule (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_code        VARCHAR(64) NOT NULL,
    version          INT         NOT NULL DEFAULT 1,
    status           VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from   DATE,
    component_a      VARCHAR(64) NOT NULL,
    component_b      VARCHAR(64) NOT NULL,
    min_gap_minutes  INT         NOT NULL,
    reason           VARCHAR(255),
    source_ref       VARCHAR(255) NOT NULL,
    source_date      DATE         NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_spacing_rule UNIQUE (rule_code, version)
);
CREATE INDEX idx_spacing_a ON spacing_rule (component_a, status);
CREATE INDEX idx_spacing_b ON spacing_rule (component_b, status);
