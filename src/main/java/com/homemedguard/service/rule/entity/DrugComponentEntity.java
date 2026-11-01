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
import java.time.LocalDateTime;

/**
 * 药品 → 标准成分（多对多，含每单位剂型含量）。
 * owner 是 ②：① 建好 drug_dict 后发 DrugRegisteredEvent，这里补一行归一化结果；
 * resolve_status=PENDING 表示自建药成分未能自动归一，评估时必须走"未识别成分"分支。
 */
@Getter
@Setter
@TableName("drug_component")
public class DrugComponentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long drugDictId;

    private Long componentId;

    private BigDecimal contentPerForm;

    private String formUnit;

    private String resolveStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
