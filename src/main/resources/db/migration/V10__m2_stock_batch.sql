-- ============================================================================
-- V10__m2_stock_batch.sql  ② 号段（V10–V19）：批次库存 + 双轨效期 + 处置台账 + 待处理任务
-- 语法保持 MySQL 8 与 H2(MODE=MySQL) 通用：不用 ENGINE/CHARSET/KEY(...) 内联索引
-- 表归属唯一：这 6 张表由 ② 写入，其他模块只能通过 ② 暴露的 Query 契约读
-- family_drug 不建指向 drug_dict 的外键：drug_dict 归 ①（见 V1 头注与 DrugBasicDTO），
--   此刻尚未落地；这里只存 drug_dict_id + 录入时的名称/剂型/规格快照，
--   查字典详情走 DrugQueryFacade，避免跨模块 JOIN 私有表。
-- ============================================================================

-- 药箱分层（柜→层→格），parent_id 为空表示根节点
CREATE TABLE IF NOT EXISTS storage_location (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id  BIGINT      NOT NULL,
    name       VARCHAR(64) NOT NULL,
    parent_id  BIGINT,
    sort_no    INT         NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    TINYINT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_storage_location_family ON storage_location (family_id);

-- 家庭在册药品：DICT=取自字典，USER_INPUT=完全自建，BARCODE=扫码降级后人工确认
CREATE TABLE IF NOT EXISTS family_drug (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id    BIGINT       NOT NULL,
    drug_dict_id BIGINT,
    source       VARCHAR(16)  NOT NULL DEFAULT 'DICT',
    generic_name VARCHAR(128) NOT NULL,
    trade_name   VARCHAR(128),
    dosage_form  VARCHAR(32),
    specification VARCHAR(64),
    approval_no  VARCHAR(64),
    otc_rx       VARCHAR(8),
    unit         VARCHAR(16)  NOT NULL DEFAULT 'PCS',
    min_safe_qty INT          NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_family_drug_dict UNIQUE (family_id, drug_dict_id)
);
CREATE INDEX idx_family_drug_family ON family_drug (family_id);
-- 自建药没有 drug_dict_id，上面那条唯一键对 NULL 不生效，靠 (family_id, generic_name) 查重
CREATE INDEX idx_family_drug_name ON family_drug (family_id, generic_name);

-- 开封后使用期规则：双轨时效的第二轨（handoff §2「剂型规则表配置开封后天数」）。
-- source_ref 按 §七「每条规则标注来源与整理日期」逐条留痕，覆盖不足时宁可少写。
CREATE TABLE IF NOT EXISTS open_use_rule (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    dosage_form   VARCHAR(32)  NOT NULL,
    open_use_days INT          NOT NULL,
    source_ref    VARCHAR(255),
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_open_use_rule_form UNIQUE (dosage_form)
);

INSERT INTO open_use_rule (dosage_form, open_use_days, source_ref) VALUES
    ('滴眼液', 28,  '药品说明书通用惯例；2026-11-01 ② 整理，待 ① 复核'),
    ('眼膏',   28,  '同上'),
    ('糖浆',   90,  '药品说明书通用惯例；2026-11-01 ② 整理，待 ① 复核'),
    ('口服溶液', 90, '同上'),
    ('干混悬剂', 90, '冲配后按使用说明冷藏限期，取常见 7～14 天之外的保守上限，待 ① 复核'),
    ('软膏',   180, '外用半固体开封后惯例；2026-11-01 ② 整理'),
    ('乳膏',   180, '同上'),
    ('贴剂',   90,  '透皮贴剂开封后惯例；2026-11-01 ② 整理'),
    ('栓剂',   180, '栓剂开封后惯例；2026-11-01 ② 整理'),
    ('气雾剂', 180, '定量气雾剂开封后惯例；2026-11-01 ② 整理'),
    ('胰岛素笔芯', 28, '胰岛素开封后 4 周通用惯例；2026-11-01 ② 整理');

-- 批次库存：同一药品可有多个不同效期批次，FEFO 按 expiry_date 升序消费
CREATE TABLE IF NOT EXISTS stock_batch (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id        BIGINT       NOT NULL,
    family_drug_id   BIGINT       NOT NULL,
    location_id      BIGINT,
    batch_no         VARCHAR(64),
    expiry_date      DATE         NOT NULL,
    opened_date      DATE,
    use_by_date      DATE,
    quantity         INT          NOT NULL DEFAULT 0,
    initial_quantity INT          NOT NULL DEFAULT 0,
    unit             VARCHAR(16)  NOT NULL DEFAULT 'PCS',
    purchase_price   DECIMAL(10, 2),
    purchase_date    DATE,
    status           VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    next_action_date DATE,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_stock_batch_identity UNIQUE (family_drug_id, batch_no, expiry_date)
);
CREATE INDEX idx_stock_batch_family_status ON stock_batch (family_id, status);
CREATE INDEX idx_stock_batch_drug_expiry ON stock_batch (family_drug_id, expiry_date);
CREATE INDEX idx_stock_batch_next_action ON stock_batch (next_action_date);

-- 处置台账：只增不改，口径供 M6 浪费率报表使用（按购入价，见 handoff §8）
CREATE TABLE IF NOT EXISTS disposal_record (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id        BIGINT       NOT NULL,
    batch_id         BIGINT       NOT NULL,
    action           VARCHAR(20)  NOT NULL,
    reason           VARCHAR(255),
    amount           DECIMAL(10, 2),
    operator_user_id BIGINT,
    disposal_date    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_disposal_record_family_date ON disposal_record (family_id, disposal_date);
CREATE INDEX idx_disposal_record_batch ON disposal_record (batch_id);

-- 待处理任务：每日扫描写入。UNIQUE(batch_id, task_type, biz_date) 是幂等键，
-- 扫描重跑靠它去重，不是靠"先查后插"。M2 的任务一律挂批次，非批次任务（复诊提醒等）归 M5。
CREATE TABLE IF NOT EXISTS action_task (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id        BIGINT       NOT NULL,
    batch_id         BIGINT       NOT NULL,
    task_type        VARCHAR(32)  NOT NULL,
    biz_date         DATE         NOT NULL,
    level            VARCHAR(16),
    title            VARCHAR(128),
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    handler_user_id  BIGINT,
    handled_at       DATETIME,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_action_task_idempotent UNIQUE (batch_id, task_type, biz_date)
);
CREATE INDEX idx_action_task_family_status ON action_task (family_id, status, biz_date);
