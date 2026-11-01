package com.homemedguard.service.stock.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.service.stock.BatchStatus;
import com.homemedguard.service.stock.StockModule;
import com.homemedguard.service.stock.api.StockQueryFacade;
import com.homemedguard.service.stock.entity.FamilyDrugEntity;
import com.homemedguard.service.stock.entity.StockBatchEntity;
import com.homemedguard.service.stock.mapper.FamilyDrugMapper;
import com.homemedguard.service.stock.mapper.StockBatchMapper;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@StockModule
public class StockQueryFacadeImpl implements StockQueryFacade {

    private final StockBatchMapper batches;
    private final FamilyDrugMapper drugs;

    public StockQueryFacadeImpl(StockBatchMapper batches, FamilyDrugMapper drugs) {
        this.batches = batches;
        this.drugs = drugs;
    }

    @Override
    public List<BatchView> availableBatches(Long familyDrugId, LocalDate asOf) {
        return toViews(batches.selectList(Wrappers.<StockBatchEntity>lambdaQuery()
                        .eq(StockBatchEntity::getFamilyDrugId, familyDrugId)
                        .gt(StockBatchEntity::getQuantity, 0))
                .stream()
                .filter(b -> !BatchStatus.isTerminal(b.getStatus()))
                .filter(b -> b.getUseByDate() == null || !b.getUseByDate().isBefore(asOf))
                .sorted(Comparator.comparing(StockBatchEntity::getUseByDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList());
    }

    @Override
    public Optional<BatchView> batch(Long batchId) {
        return Optional.ofNullable(batchId == null ? null : batches.selectById(batchId))
                .map(b -> toViews(List.of(b)))
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0));
    }

    @Override
    public int remainingQty(Long familyDrugId, LocalDate asOf) {
        return availableBatches(familyDrugId, asOf).stream().mapToInt(BatchView::quantity).sum();
    }

    @Override
    public List<BatchView> dueForInspection(LocalDate bizDate) {
        LambdaQueryWrapper<StockBatchEntity> query = Wrappers.<StockBatchEntity>lambdaQuery()
                .isNotNull(StockBatchEntity::getNextActionDate)
                .le(StockBatchEntity::getNextActionDate, bizDate);
        return toViews(batches.selectList(query).stream()
                .filter(b -> !BatchStatus.isTerminal(b.getStatus()))
                .toList());
    }

    /** 名称一次批量取，别在循环里查 family_drug。 */
    private List<BatchView> toViews(List<StockBatchEntity> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> drugIds = rows.stream().map(StockBatchEntity::getFamilyDrugId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = drugIds.isEmpty() ? Map.of()
                : drugs.selectByIds(drugIds).stream()
                .collect(Collectors.toMap(FamilyDrugEntity::getId, FamilyDrugEntity::getGenericName, (a, b) -> a));
        return rows.stream()
                .map(b -> new BatchView(b.getId(), b.getFamilyId(), b.getFamilyDrugId(), b.getBatchNo(),
                        b.getExpiryDate(), b.getOpenedDate(), b.getUseByDate(),
                        b.getQuantity() == null ? 0 : b.getQuantity(), b.getUnit(), b.getStatus(),
                        names.get(b.getFamilyDrugId())))
                .toList();
    }
}
