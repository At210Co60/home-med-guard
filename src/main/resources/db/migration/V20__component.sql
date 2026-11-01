-- ============================================================================
-- V20__component.sql  ② 号段：成分字典与归一化（drug_component / component_limit 归 ② 所有）
-- 只放判定必需的最低信息；每条剂量上限必须带 source_ref + source_date，宁缺勿错
-- ============================================================================

CREATE TABLE IF NOT EXISTS component_dict (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    component_code  VARCHAR(64)  NOT NULL,
    standard_name   VARCHAR(128) NOT NULL,
    /* 剂量单位：mg / g / mcg / IU，入库前先归一，见 UnitConverter */
    dose_unit       VARCHAR(8)   NOT NULL DEFAULT 'mg',
    /* 按成分治疗窗粗分，供"重复成分叠加"定位：解热镇痛/降压/降糖/抗生素等 */
    therapeutic_group VARCHAR(64),
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_component_dict_code UNIQUE (component_code)
);

-- 别名统一映射到标准成分："对乙酰氨基酚 / 扑热息痛 / acetaminophen / APAP" → ACETAMINOPHEN
CREATE TABLE IF NOT EXISTS component_alias (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    alias        VARCHAR(128) NOT NULL,
    component_id BIGINT       NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_component_alias UNIQUE (alias)
);
CREATE INDEX idx_component_alias_comp ON component_alias (component_id);

-- 药品 → 成分多对多；复方靠 content_per_form 拆回每味成分的实际摄入量
CREATE TABLE IF NOT EXISTS drug_component (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    drug_dict_id      BIGINT        NOT NULL,
    component_id      BIGINT        NOT NULL,
    /* 每个最小剂型单位含该成分的量（如每片含对乙酰氨基酚 500mg） */
    content_per_form  DECIMAL(12,4) NOT NULL,
    /* 计数单位：片 / 粒 / mL / 袋 */
    form_unit         VARCHAR(16)   NOT NULL DEFAULT '片',
    resolve_status    VARCHAR(16)   NOT NULL DEFAULT 'CONFIRMED',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_drug_component UNIQUE (drug_dict_id, component_id)
);
CREATE INDEX idx_drug_component_comp ON drug_component (component_id);

-- 剂量上限：成人 mg/d、儿童 mg·kg⁻¹·d⁻¹、老年与孕产用 reduce_factor 折减
CREATE TABLE IF NOT EXISTS component_limit (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    component_id         BIGINT        NOT NULL,
    /* ADULT / CHILD / ELDERLY / PREGNANT / LACTATING / HEPATIC_IMPAIRED / RENAL_IMPAIRED */
    population           VARCHAR(24)   NOT NULL,
    age_max_months       INT,
    max_daily_mg         DECIMAL(12,4),
    max_daily_mg_per_kg  DECIMAL(10,4),
    /* 相对 ADULT 上限的折减系数，1.00 表示不折减 */
    reduce_factor        DECIMAL(4,2)  NOT NULL DEFAULT 1.00,
    source_ref           VARCHAR(255)  NOT NULL,
    source_date          DATE          NOT NULL,
    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_component_limit UNIQUE (component_id, population)
);
CREATE INDEX idx_component_limit_comp ON component_limit (component_id);
