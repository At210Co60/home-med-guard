-- ============================================================================
-- V1__init.sql  底座/① 号段：身份、家庭、成员档案、系统参数、审计
-- 语法保持 MySQL 8 与 H2(MODE=MySQL) 通用：不用 ENGINE/CHARSET/KEY(...) 内联索引
-- 表归属唯一：这 6 张表由 ① 写入，②③ 只能通过 Query 契约读
-- ============================================================================

CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(64),
    role          VARCHAR(32)  NOT NULL DEFAULT 'MEMBER',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS family (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(64) NOT NULL,
    owner_user_id BIGINT      NOT NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_family_owner ON family (owner_user_id);

CREATE TABLE IF NOT EXISTS family_member (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id  BIGINT      NOT NULL,
    user_id    BIGINT,
    role       VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    relation   VARCHAR(32),
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    TINYINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_family_member_user UNIQUE (family_id, user_id)
);
CREATE INDEX idx_family_member_family ON family_member (family_id);

-- 健康档案：敏感列以密文存放（AES-256-GCM TypeHandler 由 ① 接入），列宽按密文预留
CREATE TABLE IF NOT EXISTS family_member_profile (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id              BIGINT       NOT NULL,
    display_name           VARCHAR(64),
    birth_date             DATE,
    gender                 VARCHAR(16),
    weight_kg              DECIMAL(5, 2),
    tag_codes              VARCHAR(512),
    allergy_history_enc    VARCHAR(512),
    chronic_conditions_enc VARCHAR(512),
    liver_grade            VARCHAR(8),
    renal_egfr             INT,
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_member_profile_member UNIQUE (member_id)
);

-- family_id = 0 表示全局默认值，非 0 是该家庭的覆盖值
CREATE TABLE IF NOT EXISTS sys_config (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key   VARCHAR(128) NOT NULL,
    config_value VARCHAR(512),
    value_type   VARCHAR(16)  NOT NULL DEFAULT 'STRING',
    family_id    BIGINT       NOT NULL DEFAULT 0,
    description  VARCHAR(255),
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_config_key_scope UNIQUE (config_key, family_id)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_user_id BIGINT,
    family_id        BIGINT,
    action           VARCHAR(64) NOT NULL,
    target_type      VARCHAR(64),
    target_id        VARCHAR(64),
    before_json      TEXT,
    after_json       TEXT,
    ip               VARCHAR(64),
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_log_family_time ON audit_log (family_id, created_at);

INSERT INTO sys_config (config_key, config_value, value_type, family_id, description) VALUES
    ('expiry.warn.days', '180', 'INT', 0, '临期预警阈值（天），家庭可覆盖'),
    ('risk.confirm.blocking', 'true', 'BOOL', 0, '命中高危冲突时是否强制二次确认'),
    ('disclaimer.version', '2026-11-01', 'STRING', 0, '免责声明版本号，变更需用户重新确认');
