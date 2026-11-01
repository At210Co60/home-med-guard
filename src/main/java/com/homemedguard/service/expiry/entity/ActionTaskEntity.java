package com.homemedguard.service.expiry.entity;

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

/**
 * 待处理任务。幂等键是 (batchId, taskType, bizDate)，由唯一约束兜住——
 * 扫描任务重跑靠 insert 撞键，不要改成"先查后插"。
 */
@Getter
@Setter
@TableName("action_task")
public class ActionTaskEntity {

    /** 任务类型与档位的取值口径见 ExpiryStateMachine，那里是唯一出处。 */

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_SKIPPED = "SKIPPED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;

    private Long batchId;

    private String taskType;

    /** 任务所属业务日，同日重跑不产生第二条。 */
    private LocalDate bizDate;

    private String level;

    private String title;

    private String status;

    private Long handlerUserId;

    private LocalDateTime handledAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
