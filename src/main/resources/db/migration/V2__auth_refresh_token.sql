-- ============================================================================
-- V2__auth_refresh_token.sql  ① 号段：Refresh Token 落库，支持吊销
-- 库里只存 SHA-256 摘要，不存明文：仓库泄露 / 拖库都换不来可用的 token
-- ============================================================================

CREATE TABLE IF NOT EXISTS refresh_token (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  DATETIME     NOT NULL,
    revoked     TINYINT      NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);
