package com.homemedguard.service.family.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 邀请码：usedBy 非空即已被人使用，过期时间由应用校验。 */
@Getter
@Setter
@TableName("family_invite")
public class FamilyInviteEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;

    private String code;

    private Long createdBy;

    private LocalDateTime expiresAt;

    private Long usedBy;

    private LocalDateTime usedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
