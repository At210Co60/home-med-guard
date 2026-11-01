package com.homemedguard.service.rule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 评估留痕。写它只为追溯与报表，③ 的报表模块不得回写。 */
@Getter
@Setter
@TableName("risk_evaluation_log")
public class RiskEvaluationLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;

    private Long familyId;

    private Long memberId;

    private String ruleSetStamp;

    private String scenario;

    private Integer hitCount;

    private String maxSeverity;

    /** 参与判定的最小变量快照 JSON：成分码、日剂量 mg、标签码，不含病历原文 */
    private String inputSnapshot;

    private String hitsJson;

    private Integer failedRuleCount;

    private LocalDateTime evaluatedAt;
}
