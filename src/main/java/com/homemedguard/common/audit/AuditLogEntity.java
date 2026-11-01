package com.homemedguard.common.audit;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("audit_log")
public class AuditLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operatorUserId;

    private Long familyId;

    private String action;

    private String targetType;

    private String targetId;

    /** 入参快照（已脱敏） */
    private String beforeJson;

    /** 返回值快照（已脱敏） */
    private String afterJson;

    private String ip;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
