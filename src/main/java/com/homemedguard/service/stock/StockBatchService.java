package com.homemedguard.service.stock;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.error.BizException;
import com.homemedguard.service.stock.entity.FamilyDrugEntity;
import com.homemedguard.service.stock.entity.OpenUseRuleEntity;
import com.homemedguard.service.stock.entity.StockBatchEntity;
import com.homemedguard.service.stock.mapper.FamilyDrugMapper;
import com.homemedguard.service.stock.mapper.OpenUseRuleMapper;
import com.homemedguard.service.stock.mapper.StockBatchMapper;
import com.homemedguard.service.stock.FefoAllocator.Allocation;
import com.homemedguard.service.stock.FefoAllocator.Batch;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 批次库存的写入口：入库、开封、FEFO 出库。
 * 跨家庭数据校验放在事务内（handoff §10），批次只认自己家庭在册药下的那一个 drug。
 */
@StockModule
public class StockBatchService {

    /**
     * @param openedDate 入库即已开封时非空，未开封传 null
     */
    public record IntakeCommand(Long familyId,
                                Long familyDrugId,
                                Long locationId,
                                String batchNo,
                                LocalDate expiryDate,
                                LocalDate openedDate,
                                Integer quantity,
                                BigDecimal purchasePrice,
                                LocalDate purchaseDate) {
    }

    private final StockBatchMapper batches;
    private final FamilyDrugMapper drugs;
    private final OpenUseRuleMapper openUseRules;
    private final ExpiryCalculator calculator;

    public StockBatchService(StockBatchMapper batches,
                             FamilyDrugMapper drugs,
                             OpenUseRuleMapper openUseRules,
                             ExpiryCalculator calculator) {
        this.batches = batches;
        this.drugs = drugs;
        this.openUseRules = openUseRules;
        this.calculator = calculator;
    }

    /** 同药同批号同效期重复入库，返回首次那条——幂等靠唯一键，不靠先查后插。 */
    @Transactional
    public StockBatchEntity intake(IntakeCommand cmd) {
        FamilyDrugEntity drug = requireDrug(cmd.familyId(), cmd.familyDrugId());
        if (cmd.expiryDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "有效期不能为空");
        }
        if (cmd.quantity() == null || cmd.quantity() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "入库数量必须大于 0");
        }

        StockBatchEntity batch = new StockBatchEntity();
        batch.setFamilyId(cmd.familyId());
        batch.setFamilyDrugId(drug.getId());
        batch.setLocationId(cmd.locationId());
        batch.setBatchNo(cmd.batchNo());
        batch.setExpiryDate(cmd.expiryDate());
        batch.setOpenedDate(cmd.openedDate());
        batch.setUseByDate(calculator.useByDate(cmd.expiryDate(), cmd.openedDate(),
                openUseDays(drug.getDosageForm())));
        batch.setQuantity(cmd.quantity());
        batch.setInitialQuantity(cmd.quantity());
        batch.setUnit(drug.getUnit());
        batch.setPurchasePrice(cmd.purchasePrice());
        batch.setPurchaseDate(cmd.purchaseDate());
        batch.setStatus(BatchStatus.NORMAL);
        // 入库当天就交给扫描判一次档，之后的巡检日由状态机安排
        batch.setNextActionDate(calculator.today());
        try {
            batches.insert(batch);
            return batch;
        } catch (DuplicateKeyException ex) {
            return firstOfSameIdentity(drug.getId(), cmd.batchNo(), cmd.expiryDate());
        }
    }

    /** 开封：第二轨生效，重算最终日并把巡检提前到今天。 */
    @Transactional
    public StockBatchEntity markOpened(Long batchId, LocalDate openedDate) {
        StockBatchEntity batch = requireBatch(batchId);
        if (openedDate == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "开封日期不能为空");
        }
        FamilyDrugEntity drug = requireDrug(batch.getFamilyId(), batch.getFamilyDrugId());
        batch.setOpenedDate(openedDate);
        batch.setUseByDate(calculator.useByDate(batch.getExpiryDate(), openedDate,
                openUseDays(drug.getDosageForm())));
        batch.setNextActionDate(calculator.today());
        batches.updateById(batch);
        return batch;
    }

    /**
     * FEFO 出库：按最终日升序扣减，数量不足整笔失败，不做部分扣减。
     * 扣完的批次置 DEPLETED，不再参与后续消耗与巡检。
     */
    @Transactional
    public List<Allocation> consume(Long familyId, Long familyDrugId, int required, LocalDate asOf) {
        requireDrug(familyId, familyDrugId);
        if (required <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "出库数量必须大于 0");
        }
        List<StockBatchEntity> candidates = availableBatches(familyDrugId, asOf);
        List<Allocation> plan = FefoAllocator.plan(
                candidates.stream().map(b -> new Batch(b.getId(), b.getUseByDate(), b.getQuantity())).toList(),
                required, asOf);
        if (FefoAllocator.totalAllocated(plan) < required) {
            throw new BizException(ErrorCode.PARAM_INVALID, "可用库存不足：需要 " + required
                    + "，实际可出 " + FefoAllocator.totalAllocated(plan) + "（已过期批次不参与出库）");
        }
        for (Allocation allocation : plan) {
            if (batches.decreaseQuantity(allocation.batchId(), allocation.quantity()) == 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "批次 " + allocation.batchId() + " 余量已被其他操作改变，请重试");
            }
            StockBatchEntity batch = batches.selectById(allocation.batchId());
            if (batch != null && batch.getQuantity() != null && batch.getQuantity() == 0) {
                batch.setStatus(BatchStatus.DEPLETED);
                batch.setNextActionDate(null);
                batches.updateById(batch);
            }
        }
        return plan;
    }

    public List<StockBatchEntity> availableBatches(Long familyDrugId, LocalDate asOf) {
        return batches.selectList(Wrappers.<StockBatchEntity>lambdaQuery()
                        .eq(StockBatchEntity::getFamilyDrugId, familyDrugId)
                        .gt(StockBatchEntity::getQuantity, 0))
                .stream()
                .filter(b -> !BatchStatus.isTerminal(b.getStatus()))
                .filter(b -> b.getUseByDate() == null || !b.getUseByDate().isBefore(asOf))
                .toList();
    }

    /** 剂型没有开封规则时返回 null，由 ExpiryCalculator 退回有效期。 */
    private Integer openUseDays(String dosageForm) {
        if (dosageForm == null || dosageForm.isBlank()) {
            return null;
        }
        OpenUseRuleEntity rule = openUseRules.selectOne(Wrappers.<OpenUseRuleEntity>lambdaQuery()
                .eq(OpenUseRuleEntity::getDosageForm, dosageForm)
                .last("limit 1"));
        return rule == null ? null : rule.getOpenUseDays();
    }

    private FamilyDrugEntity requireDrug(Long familyId, Long familyDrugId) {
        if (familyId == null || familyDrugId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "familyId 与 familyDrugId 都不能为空");
        }
        FamilyDrugEntity drug = drugs.selectById(familyDrugId);
        if (drug == null || !drug.getFamilyId().equals(familyId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "该药品不属于这个家庭，拒绝跨家庭操作");
        }
        return drug;
    }

    private StockBatchEntity requireBatch(Long batchId) {
        StockBatchEntity batch = batchId == null ? null : batches.selectById(batchId);
        if (batch == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "批次不存在：" + batchId);
        }
        return batch;
    }

    private StockBatchEntity firstOfSameIdentity(Long familyDrugId, String batchNo, LocalDate expiryDate) {
        LambdaQueryWrapper<StockBatchEntity> query = Wrappers.<StockBatchEntity>lambdaQuery()
                .eq(StockBatchEntity::getFamilyDrugId, familyDrugId)
                .eq(StockBatchEntity::getExpiryDate, expiryDate);
        if (batchNo == null) {
            query.isNull(StockBatchEntity::getBatchNo);
        } else {
            query.eq(StockBatchEntity::getBatchNo, batchNo);
        }
        StockBatchEntity first = batches.selectOne(query.last("limit 1"));
        if (first == null) {
            throw new BizException(ErrorCode.IDEMPOTENT_CONFLICT);
        }
        return first;
    }
}
