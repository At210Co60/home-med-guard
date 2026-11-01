package com.homemedguard.service.rule.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.homemedguard.service.rule.governance.GovernedRule;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 药-药 / 药-食 / 药-酒相互作用规则行。发布后不可原地改，改动只能开新版本。 */
@Getter
@Setter
@TableName("interaction_rule")
public class InteractionRuleEntity implements GovernedRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleCode;

    private Integer version;

    /** DRAFT / PUBLISHED / RETIRED */
    private String status;

    private LocalDate effectiveFrom;

    /** DRUG / FOOD / ALCOHOL */
    private String targetType;

    private String componentA;

    private String componentB;

    /** A=严重 B=慎用 C=注意 */
    private String severity;

    private String mechanism;

    private String advice;

    /** Aviator 附加条件，空表示命中即触发 */
    private String expr;

    private String sourceRef;

    private LocalDate sourceDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
