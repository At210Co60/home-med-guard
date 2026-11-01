package com.homemedguard.service.stock.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 处置台账。只增不改（对齐底座 audit_log 的做法，没有 updated_at / deleted），
 * 撤销一次处置用反向新记录，保证 M6 浪费率口径可追溯。
 */
@Getter
@Setter
@TableName("disposal_record")
public class DisposalRecordEntity {

    /** 过期丢弃。 */
    public static final String ACTION_DISPOSED = "DISPOSED";
    /** 效期更正（录错或换了新批次）。 */
    public static final String ACTION_RENEWED = "RENEWED";
    /** 已妥善处理，不产生损失。 */
    public static final String ACTION_HANDLED = "HANDLED";
    /** 交回药品回收点（抗生素/精神类等特殊药品）。 */
    public static final String ACTION_RETURNED = "RETURNED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;

    private Long batchId;

    private String action;

    private String reason;

    /** 损失金额，按购入价计（口径见 handoff §8）。 */
    private BigDecimal amount;

    private Long operatorUserId;

    private LocalDateTime disposalDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
