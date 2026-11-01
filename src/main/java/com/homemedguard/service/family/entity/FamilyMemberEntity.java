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

/**
 * 家庭里的一个"人"：可以是有账号的成员（userId 非空），也可以是无账号的老人/儿童（userId 为空，只有档案）。
 * role：FAMILY_ADMIN / MEMBER。relation 是与家庭的关系标签（SELF/MOTHER/FATHER/CHILD...）。
 */
@Getter
@Setter
@TableName("family_member")
public class FamilyMemberEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;

    private Long userId;

    private String role;

    private String relation;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
