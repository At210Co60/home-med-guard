-- ============================================================================
-- V4__drug_dict.sql  ① 号段：标准药品字典 + 条码映射 + 用户自建药
-- source=DICT 系统字典（只允许 ROLE_ADMIN 改），source=USER_INPUT 用户自建（只自己可见）
-- approval_no 一律由人工核对后填，代码和种子数据都不许编造批准文号
-- ============================================================================

CREATE TABLE IF NOT EXISTS drug_dict (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    generic_name  VARCHAR(128) NOT NULL,
    trade_name    VARCHAR(128),
    dosage_form   VARCHAR(32),
    specification VARCHAR(64),
    manufacturer  VARCHAR(128),
    approval_no   VARCHAR(64),
    otc_rx        VARCHAR(8)   NOT NULL DEFAULT 'OTC',
    source        VARCHAR(16)  NOT NULL DEFAULT 'DICT',
    owner_user_id BIGINT,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_drug_dict_generic ON drug_dict (generic_name);
CREATE INDEX idx_drug_dict_owner ON drug_dict (owner_user_id);

-- 追溯码/条形码 → 字典项。字典里没有时接口返回 NOT_FOUND，前端降级为"手工确认并入库"
CREATE TABLE IF NOT EXISTS drug_barcode (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    barcode      VARCHAR(64) NOT NULL,
    drug_dict_id BIGINT      NOT NULL,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_drug_barcode UNIQUE (barcode)
);
CREATE INDEX idx_drug_barcode_dict ON drug_barcode (drug_dict_id);
