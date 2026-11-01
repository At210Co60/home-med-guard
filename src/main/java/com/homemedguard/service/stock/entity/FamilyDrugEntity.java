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

/**
 * 家庭在册药品。drugDictId 只作来源锚点，不建外键也不 JOIN ① 的 drug_dict；
 * 名称/剂型/规格是录入时的快照，字典详情走 DrugQueryFacade 查。
 */
@Getter
@Setter
@TableName("family_drug")
public class FamilyDrugEntity {

    /** 取自标准字典。 */
    public static final String SOURCE_DICT = "DICT";
    /** 用户完全自建（字典没有这个药）。 */
    public static final String SOURCE_USER_INPUT = "USER_INPUT";
    /** 扫码命中失败后人工确认入库。 */
    public static final String SOURCE_BARCODE = "BARCODE";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;

    private Long drugDictId;

    private String source;

    private String genericName;

    private String tradeName;

    private String dosageForm;

    private String specification;

    private String approvalNo;

    private String otcRx;

    private String unit;

    /** 低于该余量进入补货建议（消耗推算本身归 M5）。 */
    private Integer minSafeQty;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
