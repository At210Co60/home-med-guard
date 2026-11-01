package com.homemedguard.service.rule.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("rule_publish_log")
public class RulePublishLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** interaction / contraindication / spacing */
    private String ruleTable;

    private String ruleCode;

    private Integer ruleVersion;

    /** CREATE / SUBMIT / APPROVE / REJECT / RETIRE */
    private String action;

    private Long actorUserId;

    private String reason;

    /** 动状态那一刻的规则行快照，事后被改也还能回溯 */
    private String snapshotJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
