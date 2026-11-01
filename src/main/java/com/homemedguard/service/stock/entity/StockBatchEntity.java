package com.homemedguard.service.stock.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 批次库存：同一药可有多个不同效期批次，FEFO 按 expiryDate 升序消费。
 * useByDate 是双轨合并后的最终日，由 ② 计算并冗余存储，扫描任务直接吃它。
 */
@Getter
@Setter
@TableName("stock_batch")
public class StockBatchEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;

    private Long familyDrugId;

    private Long locationId;

    private String batchNo;

    /** 包装标注有效期。 */
    private LocalDate expiryDate;

    /** 未开封为空。 */
    private LocalDate openedDate;

    /** min(expiryDate, openedDate + 剂型开封后天数)。 */
    private LocalDate useByDate;

    private Integer quantity;

    private Integer initialQuantity;

    private String unit;

    private BigDecimal purchasePrice;

    private LocalDate purchaseDate;

    /** NORMAL / EXPIRING_NEAR / EXPIRED / DISPOSED / RENEWED / HANDLED / DEPLETED。 */
    private String status;

    /** 下一次该被扫描到的日子，处置完由 ② 置空。 */
    private LocalDate nextActionDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
