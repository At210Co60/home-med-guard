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

/** 说明书要点（按标准成分 + 章节）。风险报告里的"应急指引"直接取自这张表。 */
@Getter
@Setter
@TableName("drug_leaflet")
public class DrugLeafletEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long componentId;

    /** CONTRAINDICATION / PREGNANT / ADVERSE / INTERACTION / OVERDOSE_ACTION / SEE_DOCTOR */
    private String sectionCode;

    private String content;

    private String sourceRef;

    private LocalDate sourceDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
