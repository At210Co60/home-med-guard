package com.homemedguard.service.expiry.web;

import com.homemedguard.common.access.FamilyAccessGuard;
import com.homemedguard.common.audit.AuditLog;
import com.homemedguard.common.api.Result;
import com.homemedguard.job.expiry.ExpiryScanJob;
import com.homemedguard.service.expiry.ActionTaskService;
import com.homemedguard.service.expiry.ExpiryModule;
import com.homemedguard.service.expiry.entity.ActionTaskEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 效期预警入口（M2）。扫描既能被 cron 触发也能从这里手动跑一次，
 * 答辩时不必等到凌晨 3 点05 分。
 */
@Tag(name = "06-效期预警")
@ExpiryModule
@RestController
@Validated
@RequestMapping("/api/expiry")
public class ExpiryController {

    public record TaskView(Long id,
                           Long batchId,
                           String taskType,
                           String level,
                           String title,
                           LocalDate bizDate,
                           String status) {

        static TaskView of(ActionTaskEntity task) {
            return new TaskView(task.getId(), task.getBatchId(), task.getTaskType(), task.getLevel(),
                    task.getTitle(), task.getBizDate(), task.getStatus());
        }
    }

    private final ActionTaskService tasks;
    private final ExpiryScanJob scanJob;
    private final FamilyAccessGuard access;

    public ExpiryController(ActionTaskService tasks, ExpiryScanJob scanJob, FamilyAccessGuard access) {
        this.tasks = tasks;
        this.scanJob = scanJob;
        this.access = access;
    }

    @Operation(summary = "未处理的效期任务，按业务日倒序")
    @GetMapping("/tasks")
    public Result<List<TaskView>> pending(@RequestParam(required = false) Long familyId,
                                         @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(tasks.pending(access.requireFamilyId(familyId), limit).stream()
                .map(TaskView::of).toList());
    }

    @Operation(summary = "手动触发一次效期扫描（幂等：同日重跑不产生第二条任务）")
    @PostMapping("/scan")
    public Result<Integer> scan(@RequestParam(required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        return Result.ok(scanJob.scan(bizDate == null ? LocalDate.now() : bizDate));
    }

    @Operation(summary = "确认看过但不处置（例如已决定继续使用），置为 SKIPPED")
    @AuditLog(action = "TASK_SKIP", targetType = "action_task")
    @PostMapping("/tasks/{id}/skip")
    public Result<Void> skip(@PathVariable Long id, @RequestParam(required = false) Long familyId) {
        tasks.skip(id, access.requireFamilyId(familyId));
        return Result.ok();
    }
}
