package com.homemedguard.job.expiry;

import com.homemedguard.common.setting.SettingService;
import com.homemedguard.service.expiry.ExpiryModule;
import com.homemedguard.service.expiry.ExpiryStateMachine;
import com.homemedguard.service.expiry.ExpiryStateMachine.Verdict;
import com.homemedguard.service.expiry.entity.ActionTaskEntity;
import com.homemedguard.service.expiry.mapper.ActionTaskMapper;
import com.homemedguard.service.stock.api.StockMaintenanceFacade;
import com.homemedguard.service.stock.api.StockQueryFacade;
import com.homemedguard.service.stock.api.StockQueryFacade.BatchView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 每日效期扫描（handoff §3，cron 0 5 3 * * ?）。
 * 幂等靠 action_task 的唯一键 (batch_id, task_type, biz_date)：撞键就当今天已经出过这条，
 * 不做"先查后插"——那会在并发重跑时留下双份。
 */
@ExpiryModule
public class ExpiryScanJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiryScanJob.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 键不存在时用代码默认，不往 ① 的 sys_config 里插行。 */
    static final String KEY_ADVISORY = "expiry.warn.days";
    static final String KEY_WARNING = "expiry.warn.mid.days";
    static final String KEY_URGENT = "expiry.warn.near.days";

    private final StockQueryFacade stock;
    private final StockMaintenanceFacade maintenance;
    private final ExpiryStateMachine stateMachine;
    private final ActionTaskMapper tasks;
    private final SettingService settings;

    public ExpiryScanJob(StockQueryFacade stock,
                         StockMaintenanceFacade maintenance,
                         ExpiryStateMachine stateMachine,
                         ActionTaskMapper tasks,
                         SettingService settings) {
        this.stock = stock;
        this.maintenance = maintenance;
        this.stateMachine = stateMachine;
        this.tasks = tasks;
        this.settings = settings;
    }

    @Scheduled(cron = "0 5 3 * * ?")
    public void scanDaily() {
        int created = scan(LocalDate.now(ZONE));
        log.info("效期扫描完成，业务日 {}，新增待处理任务 {} 条", LocalDate.now(ZONE), created);
    }

    /**
     * @return 本次真正新建出来的任务条数（重复的不计）
     */
    @Transactional(rollbackFor = Exception.class)
    public int scan(LocalDate bizDate) {
        int created = 0;
        for (BatchView batch : stock.dueForInspection(bizDate)) {
            Verdict verdict = stateMachine.evaluate(bizDate, batch, thresholds(batch.familyId()));
            if (verdict.taskType() != null && insertTask(bizDate, batch, verdict)) {
                created++;
            }
            maintenance.applyExpiryVerdict(batch.batchId(), verdict.batchStatus(), verdict.nextCheckDate());
        }
        return created;
    }

    private ExpiryStateMachine.Thresholds thresholds(Long familyId) {
        return new ExpiryStateMachine.Thresholds(
                settings.intOf(KEY_ADVISORY, familyId, 180),
                settings.intOf(KEY_WARNING, familyId, 90),
                settings.intOf(KEY_URGENT, familyId, 30));
    }

    private boolean insertTask(LocalDate bizDate, BatchView batch, Verdict verdict) {
        ActionTaskEntity task = new ActionTaskEntity();
        task.setFamilyId(batch.familyId());
        task.setBatchId(batch.batchId());
        task.setTaskType(verdict.taskType());
        task.setBizDate(bizDate);
        task.setLevel(verdict.level());
        task.setTitle(title(batch, verdict));
        task.setStatus(ActionTaskEntity.STATUS_PENDING);
        try {
            tasks.insert(task);
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    /** 措辞只说"该核对处置"，不写"已确认安全"之类结论性表述（handoff §七）。 */
    private static String title(BatchView batch, Verdict verdict) {
        String drug = batch.genericName() == null ? "批次 " + batch.batchNo() : batch.genericName();
        return switch (verdict.taskType()) {
            case ExpiryStateMachine.TASK_OPENED_OVERDUE -> drug + " 开封后已超过使用期（" + batch.useByDate() + "），请核对处置";
            case ExpiryStateMachine.TASK_EXPIRED -> drug + " 已过有效期（" + batch.useByDate() + "），请核对处置";
            default -> drug + " 临近效期（" + batch.useByDate() + "），请关注";
        };
    }
}
