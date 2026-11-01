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

/** 说明书图片/附件。库里只存存储 key，不存文件内容。 */
@Getter
@Setter
@TableName("drug_attachment")
public class DrugAttachmentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long drugDictId;

    /** LEAFLET_IMAGE / OTHER */
    private String kind;

    private String storageKey;

    private String originalName;

    private String contentType;

    private Long sizeBytes;

    private Long uploadedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
