package com.homemedguard.service.stock.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * ② 批次库存对外只读契约。按 git-collaboration §7 交叉点：C 的余量推算
 * （service/forecast）只能这样拿库存，不注入本模块 Mapper、不写 stock_batch。
 * 效期模块的每日扫描也走这里，依赖方向单向：expiry -> stock。
 */
public interface StockQueryFacade {

    /**
     * @param useByDate 双轨合并后的最终日，已由本模块算好；未开封时等于有效期
     * @param quantity  当前剩余数量，不是入库量
     * @param genericName 在册药快照名，任务标题直接用，不再回查字典
     */
    record BatchView(Long batchId,
                     Long familyId,
                     Long familyDrugId,
                     String batchNo,
                     LocalDate expiryDate,
                     LocalDate openedDate,
                     LocalDate useByDate,
                     int quantity,
                     String unit,
                     String status,
                     String genericName) {
    }

    /** FEFO 顺序（useByDate 升序、期限未知的排最后）且已过期限的批次不在内。 */
    List<BatchView> availableBatches(Long familyDrugId, LocalDate asOf);

    /** 含终态批次，供处置台账与报表回溯。 */
    Optional<BatchView> batch(Long batchId);

    /** 家庭内该药当前可用余量合计。 */
    int remainingQty(Long familyDrugId, LocalDate asOf);

    /**
     * 今天该被扫到的批次：nextActionDate 已到且不在终态（DISPOSED/RENEWED/HANDLED/DEPLETED）。
     * 每日扫描只认这一个入口，不自己拼 stock_batch 的查询条件。
     */
    List<BatchView> dueForInspection(LocalDate bizDate);
}
