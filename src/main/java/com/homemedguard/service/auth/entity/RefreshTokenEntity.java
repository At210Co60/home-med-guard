package com.homemedguard.service.auth.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 只存 refresh token 的 SHA-256 摘要；吊销 = revoked 置 1，不换密钥也拿不回 token。 */
@Getter
@Setter
@TableName("refresh_token")
public class RefreshTokenEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String tokenHash;

    private LocalDateTime expiresAt;

    private Integer revoked;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
