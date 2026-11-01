package com.homemedguard.service.stock.impl;

import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.error.BizException;
import com.homemedguard.service.stock.BatchStatus;
import com.homemedguard.service.stock.StockModule;
import com.homemedguard.service.stock.api.StockMaintenanceFacade;
import com.homemedguard.service.stock.entity.StockBatchEntity;
import com.homemedguard.service.stock.mapper.StockBatchMapper;

import java.time.LocalDate;

@StockModule
public class StockMaintenanceFacadeImpl implements StockMaintenanceFacade {

    private final StockBatchMapper batches;

    public StockMaintenanceFacadeImpl(StockBatchMapper batches) {
        this.batches = batches;
    }

    @Override
    public void applyExpiryVerdict(Long batchId, String status, LocalDate nextActionDate) {
        StockBatchEntity batch = batches.selectById(batchId);
        if (batch == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "批次不存在：" + batchId);
        }
        if (BatchStatus.isTerminal(batch.getStatus())) {
            // 已经处置完的批次不再被扫描改写，否则处置台账会被巡检任务倒回去
            return;
        }
        batch.setStatus(status);
        batch.setNextActionDate(nextActionDate);
        batches.updateById(batch);
    }
}
