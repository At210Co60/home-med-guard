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

/**
 * 标准药品字典。source=DICT 系统字典（只有 ROLE_ADMIN 能改，ownerUserId 为空），
 * source=USER_INPUT 用户自建药（ownerUserId 非空，只有本人可见）。
 */
@Getter
@Setter
@TableName("drug_dict")
public class DrugDictEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String genericName;

    private String tradeName;

    private String dosageForm;

    private String specification;

    private String manufacturer;

    private String approvalNo;

    /** OTC / RX */
    private String otcRx;

    /** DICT / DEMO / USER_INPUT */
    private String source;

    private Long ownerUserId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    /** 自建药只归属人自己可见。 */
    public boolean visibleTo(Long userId, boolean sysAdmin) {
        return sysAdmin || ownerUserId == null || ownerUserId.equals(userId);
    }
}
