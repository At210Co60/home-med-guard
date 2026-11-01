package com.homemedguard.service.expiry;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.error.BizException;
import com.homemedguard.service.expiry.entity.ActionTaskEntity;
import com.homemedguard.service.expiry.mapper.ActionTaskMapper;

import java.time.LocalDate;
import java.util.List;

/** 待处理任务的读取与收尾。任务表只在这里写，扫描任务之外的地方不改它。 */
@ExpiryModule
public class ActionTaskService {

    private final ActionTaskMapper tasks;

    public ActionTaskService(ActionTaskMapper tasks) {
        this.tasks = tasks;
    }

    /** 某家庭未处理完的任务，按业务日倒序、档位高的在前。 */
    public List<ActionTaskEntity> pending(Long familyId, int limit) {
        if (familyId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "familyId 不能为空");
        }
        LambdaQueryWrapper<ActionTaskEntity> query = Wrappers.<ActionTaskEntity>lambdaQuery()
                .eq(ActionTaskEntity::getFamilyId, familyId)
                .eq(ActionTaskEntity::getStatus, ActionTaskEntity.STATUS_PENDING)
                .orderByDesc(ActionTaskEntity::getBizDate)
                .last("limit " + Math.max(1, Math.min(limit, 200)));
        return tasks.selectList(query);
    }

    /** 用户看过但不处置：置 SKIPPED，扫描第二天不再重复提醒同一条。 */
    public void skip(Long taskId, Long familyId) {
        ActionTaskEntity task = taskId == null ? null : tasks.selectById(taskId);
        if (task == null || !task.getFamilyId().equals(familyId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "任务不存在或不属于该家庭：" + taskId);
        }
        if (!ActionTaskEntity.STATUS_PENDING.equals(task.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务已是 " + task.getStatus() + "，不能重复操作");
        }
        task.setStatus(ActionTaskEntity.STATUS_SKIPPED);
        task.setHandledAt(java.time.LocalDateTime.now(com.homemedguard.service.stock.ExpiryCalculator.ZONE));
        tasks.updateById(task);
    }

    /** 批次处置完成后收尾：只关这个批次当天及以前仍未处理的任务。 */
    public int closeOpenTasks(Long batchId, LocalDate upTo, Long handlerUserId) {
        ActionTaskEntity update = new ActionTaskEntity();
        update.setStatus(ActionTaskEntity.STATUS_DONE);
        update.setHandlerUserId(handlerUserId);
        update.setHandledAt(java.time.LocalDateTime.now(com.homemedguard.service.stock.ExpiryCalculator.ZONE));
        return tasks.update(update, Wrappers.<ActionTaskEntity>lambdaUpdate()
                .eq(ActionTaskEntity::getBatchId, batchId)
                .eq(ActionTaskEntity::getStatus, ActionTaskEntity.STATUS_PENDING)
                .le(ActionTaskEntity::getBizDate, upTo));
    }
}
