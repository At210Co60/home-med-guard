-- ============================================================================
-- V24__drug_leaflet_and_attachment.sql
-- drug_leaflet：按"标准成分 + 章节"结构化的说明书要点（② 拥有，风险报告直接引用）
-- drug_attachment：说明书图片/附件（① 拥有，库里只存存储 key，文件在 data/files/ 运行目录）
-- 两个表的 content/source_ref 都必须人工核对：要点写错比没有要点更危险
-- ============================================================================

CREATE TABLE IF NOT EXISTS drug_leaflet (
    id           BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    component_id BIGINT         NOT NULL,
    /* CONTRAINDICATION / PREGNANT / ADVERSE / INTERACTION / OVERDOSE_ACTION / SEE_DOCTOR */
    section_code VARCHAR(32)    NOT NULL,
    content      VARCHAR(2000)  NOT NULL,
    source_ref   VARCHAR(255)   NOT NULL,
    source_date  DATE           NOT NULL,
    created_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_drug_leaflet UNIQUE (component_id, section_code)
);
CREATE INDEX idx_drug_leaflet_component ON drug_leaflet (component_id);

CREATE TABLE IF NOT EXISTS drug_attachment (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    drug_dict_id  BIGINT       NOT NULL,
    /* LEAFLET_IMAGE / OTHER */
    kind          VARCHAR(16)  NOT NULL DEFAULT 'LEAFLET_IMAGE',
    storage_key   VARCHAR(255) NOT NULL,
    original_name VARCHAR(255),
    content_type  VARCHAR(64),
    size_bytes    BIGINT       NOT NULL,
    uploaded_by   BIGINT       NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_drug_attachment_key UNIQUE (storage_key)
);
CREATE INDEX idx_drug_attachment_dict ON drug_attachment (drug_dict_id);
