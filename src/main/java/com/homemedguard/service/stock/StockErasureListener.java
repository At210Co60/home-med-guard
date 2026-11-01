package com.homemedguard.service.stock;

import com.homemedguard.common.contract.AccountErasureRequestedEvent;
import com.homemedguard.service.stock.mapper.DisposalRecordMapper;
import com.homemedguard.service.stock.mapper.FamilyDrugMapper;
import com.homemedguard.service.stock.mapper.StockBatchMapper;
import com.homemedguard.service.stock.mapper.StorageLocationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号注销时清 ② 批次库存这边的表。按 A 的约定：① 删完自己那部分只广播事件，
 * 各模块删自己拥有的数据——① 不跨模块 DELETE stock_batch，我们也不碰 family_member。
 *
 * <p>只在家真正被解散时清：在册药、批次、处置台账、存放位置都是家庭级数据，
 * 单个成员退出（成员是"人"，药箱是"家庭"）不该把全家药箱删掉。
 */
@StockModule
public class StockErasureListener {

    private static final Logger log = LoggerFactory.getLogger(StockErasureListener.class);

    private final FamilyDrugMapper drugs;
    private final StockBatchMapper batches;
    private final DisposalRecordMapper disposals;
    private final StorageLocationMapper locations;

    public StockErasureListener(FamilyDrugMapper drugs,
                                  StockBatchMapper batches,
                                  DisposalRecordMapper disposals,
                                  StorageLocationMapper locations) {
        this.drugs = drugs;
        this.batches = batches;
        this.disposals = disposals;
        this.locations = locations;
    }

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onAccountErasure(AccountErasureRequestedEvent event) {
        if (!event.familyDeleted() || event.familyId() == null) {
            return;
        }
        Long familyId = event.familyId();
        // 先台账与批次、再在册药、最后位置：任何一行留下来都不会变成找不到父行的孤儿
        int disposalRows = disposals.eraseByFamily(familyId);
        int batchRows = batches.eraseByFamily(familyId);
        int drugRows = drugs.eraseByFamily(familyId);
        int locationRows = locations.eraseByFamily(familyId);
        log.info("家庭 {} 注销，库存侧清除 批次 {} / 在册药 {} / 处置台账 {} / 位置 {}（任务由效期模块自清）",
                familyId, batchRows, drugRows, disposalRows, locationRows);
    }
}
