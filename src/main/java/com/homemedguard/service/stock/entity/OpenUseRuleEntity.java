package com.homemedguard.service.stock.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 剂型 → 开封后使用期（天），双轨时效的第二轨。 */
@Getter
@Setter
@TableName("open_use_rule")
public class OpenUseRuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String dosageForm;

    private Integer openUseDays;

    /** 规则出处与整理日期（handoff §七：知识库每条规则必须可溯源）。 */
    private String sourceRef;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
