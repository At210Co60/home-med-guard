package com.homemedguard.service.drug.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 追溯码/条形码 → 字典项映射。扫不出来时接口返回空，前端降级为"手工确认并入库"。 */
@Getter
@Setter
@TableName("drug_barcode")
public class DrugBarcodeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String barcode;

    private Long drugDictId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
