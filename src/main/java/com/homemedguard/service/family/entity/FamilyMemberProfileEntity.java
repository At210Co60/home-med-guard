package com.homemedguard.service.family.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.homemedguard.common.crypto.AesGcmTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 成员健康档案。allergyHistory / chronicConditions 两列在库里是密文（AES-256-GCM），
 * autoResultMap 必须开，否则读出来是密文还当明文用。
 */
@Getter
@Setter
@TableName(value = "family_member_profile", autoResultMap = true)
public class FamilyMemberProfileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long memberId;

    private String displayName;

    private LocalDate birthDate;

    private String gender;

    private BigDecimal weightKg;

    /** 禁忌标签码，逗号分隔存库；对外一律用 Set */
    private String tagCodes;

    @TableField(value = "allergy_history_enc", typeHandler = AesGcmTypeHandler.class)
    private String allergyHistory;

    @TableField(value = "chronic_conditions_enc", typeHandler = AesGcmTypeHandler.class)
    private String chronicConditions;

    private String liverGrade;

    private Integer renalEgfr;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
