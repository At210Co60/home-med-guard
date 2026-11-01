-- ============================================================================
-- V3__family_invite.sql  ① 号段：家庭组邀请码
-- used_by 非空即已使用；过期时间由应用层校验，不做定时清理（数据量极小）
-- ============================================================================

CREATE TABLE IF NOT EXISTS family_invite (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id  BIGINT      NOT NULL,
    code       VARCHAR(16) NOT NULL,
    created_by BIGINT      NOT NULL,
    expires_at DATETIME    NOT NULL,
    used_by    BIGINT,
    used_at    DATETIME,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    TINYINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_family_invite_code UNIQUE (code)
);
CREATE INDEX idx_family_invite_family ON family_invite (family_id);
