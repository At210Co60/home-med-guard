package com.homemedguard.service.rule.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 需间隔服药：② 只声明最小间隔，怎么错开时点是 ③ 排程求解器的职责。 */
@Getter
@Setter
@TableName("spacing_rule")
public class SpacingRuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleCode;

    private Integer version;

    private String status;

    private LocalDate effectiveFrom;

    private String componentA;

    private String componentB;

    private Integer minGapMinutes;

    private String reason;

    private String sourceRef;

    private LocalDate sourceDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
