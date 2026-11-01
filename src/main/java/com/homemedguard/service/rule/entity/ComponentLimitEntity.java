package com.homemedguard.service.rule.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 成分剂量上限。缺 source_ref / source_date 的行不允许发布，宁可少覆盖。 */
@Getter
@Setter
@TableName("component_limit")
public class ComponentLimitEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long componentId;

    /** ADULT / CHILD / ELDERLY / PREGNANT / LACTATING / HEPATIC_IMPAIRED / RENAL_IMPAIRED */
    private String population;

    private Integer ageMaxMonths;

    private BigDecimal maxDailyMg;

    private BigDecimal maxDailyMgPerKg;

    private BigDecimal reduceFactor;

    private String sourceRef;

    private LocalDate sourceDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
