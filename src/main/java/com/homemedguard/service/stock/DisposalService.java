package com.homemedguard.service.stock;

import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.error.BizException;
import com.homemedguard.service.stock.api.BatchDisposedEvent;
import com.homemedguard.service.stock.entity.DisposalRecordEntity;
import com.homemedguard.service.stock.entity.StockBatchEntity;
import com.homemedguard.service.stock.mapper.DisposalRecordMapper;
import com.homemedguard.service.stock.mapper.StockBatchMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * 处置流程与台账（handoff §3）。台账只增不改，撤销用反向记录，
 * 这样 M6 的浪费率口径随时可回溯到具体批次与操作人。
 */
@StockModule
public class DisposalService {

    private static final Set<String> ALLOWED = Set.of(
            DisposalRecordEntity.ACTION_DISPOSED, DisposalRecordEntity.ACTION_RENEWED,
            DisposalRecordEntity.ACTION_HANDLED, DisposalRecordEntity.ACTION_RETURNED);

    /** 回收视同已处置，批次都进 DISPOSED 终态。 */
    private static final Map<String, String> BATCH_STATUS = Map.of(
            DisposalRecordEntity.ACTION_DISPOSED, BatchStatus.DISPOSED,
            DisposalRecordEntity.ACTION_RETURNED, BatchStatus.DISPOSED,
            DisposalRecordEntity.ACTION_RENEWED, BatchStatus.RENEWED,
            DisposalRecordEntity.ACTION_HANDLED, BatchStatus.HANDLED);

    /** 只有真正丢弃/交回的才算损失，效期更正和"已妥善处理"不产生浪费金额。 */
    private static final Set<String> WASTE_ACTIONS = Set.of(
            DisposalRecordEntity.ACTION_DISPOSED, DisposalRecordEntity.ACTION_RETURNED);

    /**
     * @param amount 为空时按购入单价 × 剩余数量自动折算损失；传 0 表示确认无损失
     */
    public record DisposeCommand(Long familyId,
                                 Long batchId,
                                 String action,
                                 String reason,
                                 BigDecimal amount,
                                 Long operatorUserId) {
    }

    private final StockBatchMapper batches;
    private final DisposalRecordMapper disposals;
    private final ApplicationEventPublisher events;

    public DisposalService(StockBatchMapper batches,
                           DisposalRecordMapper disposals,
                           ApplicationEventPublisher events) {
        this.batches = batches;
        this.disposals = disposals;
        this.events = events;
    }

    @Transactional(rollbackFor = Exception.class)
    public DisposalRecordEntity dispose(DisposeCommand cmd) {
        if (cmd == null || cmd.action() == null || !ALLOWED.contains(cmd.action())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "处置动作只能是 DISPOSED/RENEWED/HANDLED/RETURNED");
        }
        StockBatchEntity batch = cmd.batchId() == null ? null : batches.selectById(cmd.batchId());
        if (batch == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "批次不存在：" + cmd.batchId());
        }
        if (!batch.getFamilyId().equals(cmd.familyId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "该批次不属于这个家庭，拒绝跨家庭操作");
        }
        if (BatchStatus.isTerminal(batch.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "批次已是终态 " + batch.getStatus() + "，需要更正请另记一条处置");
        }

        BigDecimal amount = cmd.amount() != null ? cmd.amount() : wastedValue(batch);
        LocalDate today = LocalDate.now(ExpiryCalculator.ZONE);

        DisposalRecordEntity record = new DisposalRecordEntity();
        record.setFamilyId(cmd.familyId());
        record.setBatchId(batch.getId());
        record.setAction(cmd.action());
        record.setReason(cmd.reason());
        record.setAmount(amount);
        record.setOperatorUserId(cmd.operatorUserId());
        record.setDisposalDate(java.time.LocalDateTime.now(ExpiryCalculator.ZONE));
        disposals.insert(record);

        batch.setStatus(BATCH_STATUS.get(cmd.action()));
        batch.setNextActionDate(null);
        if (WASTE_ACTIONS.contains(cmd.action())) {
            batch.setQuantity(0);
        }
        batches.updateById(batch);

        events.publishEvent(new BatchDisposedEvent(batch.getId(), cmd.familyId(), cmd.action(), today,
                WASTE_ACTIONS.contains(cmd.action()) ? amount : BigDecimal.ZERO));
        return record;
    }

    /** 剩余数量 × 购入单价；没录购入价就不臆造金额，报表侧按 0 计入分母外的损失。 */
    private static BigDecimal wastedValue(StockBatchEntity batch) {
        if (batch.getPurchasePrice() == null || batch.getQuantity() == null) {
            return BigDecimal.ZERO;
        }
        return batch.getPurchasePrice().multiply(BigDecimal.valueOf(batch.getQuantity()));
    }
}
