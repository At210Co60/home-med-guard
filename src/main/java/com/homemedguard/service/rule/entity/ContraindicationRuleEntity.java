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

/** 禁忌人群规则：tag_code 与 ① 成员档案的标签码同字典。 */
@Getter
@Setter
@TableName("contraindication_rule")
public class ContraindicationRuleEntity implements GovernedRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleCode;

    private Integer version;

    private String status;

    private LocalDate effectiveFrom;

    private String component;

    private String tagCode;

    private String severity;

    private String advice;

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
