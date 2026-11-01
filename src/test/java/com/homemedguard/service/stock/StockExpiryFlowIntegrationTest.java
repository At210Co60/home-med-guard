package com.homemedguard.service.stock;

import com.homemedguard.common.error.BizException;
import com.homemedguard.job.expiry.ExpiryScanJob;
import com.homemedguard.service.stock.api.StockQueryFacade;
import com.homemedguard.service.stock.api.StockQueryFacade.BatchView;
import com.homemedguard.service.stock.entity.FamilyDrugEntity;
import com.homemedguard.service.stock.entity.StorageLocationEntity;
import com.homemedguard.service.stock.entity.StockBatchEntity;
import com.homemedguard.service.stock.FamilyDrugService.RegisterCommand;
import com.homemedguard.service.stock.StockBatchService.IntakeCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M2 落库回验：建表脚本、FEFO 出库、双轨效期重算、每日扫描幂等串起来跑一遍。
 * 用 test profile 的 H2，不需要本机装 MySQL（application-test.yml）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StockExpiryFlowIntegrationTest {

    private static final Long FAMILY = 9001L;
    private static final LocalDate TODAY = LocalDate.now(ExpiryCalculator.ZONE);

    @Autowired
    private FamilyDrugService familyDrugs;

    @Autowired
    private StockBatchService batches;

    @Autowired
    private StockQueryFacade stockQuery;

    @Autowired
    private ExpiryScanJob scanJob;

    @Autowired
    private StorageLocationService locations;

    @Test
    @DisplayName("FEFO 跨批次出库：先到期的先出，扣完置 DEPLETED")
    void consumesEarliestExpiryFirst() {
        Long drugId = register("阿司匹林肠溶片", "片剂");
        intake(drugId, "B-EARLY", TODAY.plusDays(60), 4);
        intake(drugId, "B-LATE", TODAY.plusDays(300), 10);

        List<FefoAllocator.Allocation> plan = batches.consume(FAMILY, drugId, 6, TODAY);

        assertThat(plan).hasSize(2);
        assertThat(plan.get(0).quantity()).isEqualTo(4);
        assertThat(plan.get(1).quantity()).isEqualTo(2);
        assertThat(stockQuery.remainingQty(drugId, TODAY)).isEqualTo(8);
        assertThat(stockQuery.availableBatches(drugId, TODAY))
                .extracting(BatchView::batchNo).containsExactly("B-LATE");
    }

    @Test
    @DisplayName("已过期批次不参与出库，凑不齐就整笔失败而不是偷偷少发")
    void refusesToConsumeExpiredBatches() {
        Long drugId = register("布洛芬缓释胶囊", "胶囊");
        intake(drugId, "B-EXPIRED", TODAY.minusDays(3), 20);

        assertThatThrownBy(() -> batches.consume(FAMILY, drugId, 5, TODAY))
                .hasMessageContaining("可用库存不足");
        assertThat(stockQuery.remainingQty(drugId, TODAY)).isZero();
    }

    @Test
    @DisplayName("开封后按剂型规则重算最终日：滴眼液 28 天压过明年才到的有效期")
    void openingRecomputesTheStricterDeadline() {
        Long drugId = register("左氧氟沙星滴眼液", "滴眼液");
        StockBatchEntity batch = intake(drugId, "B-EYE", TODAY.plusDays(400), 1);

        assertThat(batch.getUseByDate()).isEqualTo(TODAY.plusDays(400));

        LocalDate opened = TODAY.minusDays(10);
        StockBatchEntity reopened = batches.markOpened(batch.getId(), opened);

        assertThat(reopened.getUseByDate()).isEqualTo(opened.plusDays(28));
    }

    @Test
    @DisplayName("同药同批号同效期重复入库返回首次那条，不落第二行")
    void intakeIsIdempotentOnTheUniqueKey() {
        Long drugId = register("维生素C片", "片剂");
        StockBatchEntity first = intake(drugId, "B-DUP", TODAY.plusDays(200), 30);

        StockBatchEntity again = intake(drugId, "B-DUP", TODAY.plusDays(200), 30);

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(stockQuery.availableBatches(drugId, TODAY)).hasSize(1);
    }

    @Test
    @DisplayName("扫描重跑不产生第二条任务：幂等靠唯一键，不是先查后插")
    void scanIsIdempotentPerBusinessDate() {
        Long drugId = register("硝苯地平缓释片", "片剂");
        intake(drugId, "B-WARN", TODAY.plusDays(20), 10);

        int firstRun = scanJob.scan(TODAY);
        int secondRun = scanJob.scan(TODAY);

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isZero();
        assertThat(stockQuery.batch(onlyBatchId(drugId)).orElseThrow().status())
                .isEqualTo("EXPIRING_NEAR");
    }

    @Test
    @DisplayName("批次标题带上在册药名，任务列表不用回查字典")
    void taskTitleCarriesTheSnapshotName() {
        Long drugId = register("阿卡波糖片", "片剂");
        intake(drugId, "B-TITLE", TODAY.plusDays(5), 3);

        scanJob.scan(TODAY);

        assertThat(stockQuery.batch(onlyBatchId(drugId)).orElseThrow().genericName())
                .isEqualTo("阿卡波糖片");
    }

    @Test
    @DisplayName("不允许拿别人家庭的药入库：跨家庭数据校验在事务内")
    void rejectsCrossFamilyDrugReference() {
        Long drugId = register("复方甘草片", "片剂");

        assertThatThrownBy(() -> batches.intake(new IntakeCommand(9999L, drugId, null,
                "B-OTHER", TODAY.plusDays(30), null, 5, null, null)))
                .hasMessageContaining("不属于这个家庭");
    }

    @Test
    @DisplayName("字典未接线时自建药照样入库，来源标记为 USER_INPUT")
    void registersSelfBuiltDrugWithoutTheDictionary() {
        FamilyDrugEntity drug = familyDrugs.register(new RegisterCommand(
                FAMILY, null, "自制外用药膏", null, "软膏", "10g", null, null, "支", 0));

        assertThat(drug.getSource()).isEqualTo(FamilyDrugEntity.SOURCE_USER_INPUT);
        assertThat(drug.getId()).isNotNull();
    }

    @Test
    @DisplayName("出库单价与购入价一起进台账口径，M6 浪费率靠它")
    void keepsPurchasePriceForWasteReporting() {
        Long drugId = register("阿莫西林胶囊", "胶囊");
        StockBatchEntity batch = batches.intake(new IntakeCommand(FAMILY, drugId, null,
                "B-PRICE", TODAY.plusDays(90), null, 12, new BigDecimal("23.50"), TODAY));

        assertThat(batch.getPurchasePrice()).isEqualByComparingTo("23.50");
        assertThat(batch.getUnit()).isEqualTo("PCS");
    }

    @Test
    @DisplayName("药箱分层：柜→层→格能建成树，改名走同一条 save")
    void buildsTheLocationTree() {
        Long cabinet = locations.save(new StorageLocationService.SaveCommand(
                null, FAMILY, null, "客厅药柜", 1)).getId();
        Long shelf = locations.save(new StorageLocationService.SaveCommand(
                null, FAMILY, cabinet, "第二层", 1)).getId();

        assertThat(locations.list(FAMILY)).hasSize(2);

        locations.save(new StorageLocationService.SaveCommand(shelf, FAMILY, cabinet, "第二层（常备）", 2));
        assertThat(locations.list(FAMILY)).extracting(StorageLocationEntity::getName)
                .contains("第二层（常备）");
    }

    @Test
    @DisplayName("不能把自己挪到自己的子树下：成环会让 list 再也列不全")
    void rejectsCyclicParent() {
        Long root = locations.save(new StorageLocationService.SaveCommand(
                null, FAMILY, null, "主卧药盒", 1)).getId();
        Long child = locations.save(new StorageLocationService.SaveCommand(
                null, FAMILY, root, "格 A", 1)).getId();

        assertThatThrownBy(() -> locations.save(new StorageLocationService.SaveCommand(
                root, FAMILY, child, "主卧药盒", 1)))
                .hasMessageContaining("环");
    }

    @Test
    @DisplayName("还停着批次的格子不许删，免得处置时人找不到药")
    void refusesToDeleteParkedLocation() {
        Long spot = locations.save(new StorageLocationService.SaveCommand(
                null, FAMILY, null, "冰箱门", 1)).getId();
        Long drugId = register("胰岛素笔芯", "胰岛素笔芯");
        batches.intake(new StockBatchService.IntakeCommand(FAMILY, drugId, spot, "B-FRIDGE",
                TODAY.plusDays(90), null, 5, null, null));

        assertThatThrownBy(() -> locations.delete(FAMILY, spot)).hasMessageContaining("批次");

        Long empty = locations.save(new StorageLocationService.SaveCommand(
                null, FAMILY, null, "空格子", 9)).getId();
        locations.delete(FAMILY, empty);
        assertThat(locations.list(FAMILY)).noneMatch(row -> row.getId().equals(empty));
    }

    @Test
    @DisplayName("别人的家庭删不掉我的格子：跨家庭一律 NOT_FOUND")
    void rejectsForeignFamilyLocation() {
        Long spot = locations.save(new StorageLocationService.SaveCommand(
                null, FAMILY, null, "我的格子", 1)).getId();

        assertThatThrownBy(() -> locations.delete(9999L, spot)).isInstanceOf(BizException.class);
    }

    private Long register(String genericName, String dosageForm) {
        return familyDrugs.register(new RegisterCommand(FAMILY, null, genericName, null,
                dosageForm, null, null, null, "PCS", 0)).getId();
    }

    private StockBatchEntity intake(Long drugId, String batchNo, LocalDate expiry, int qty) {
        return batches.intake(new IntakeCommand(FAMILY, drugId, null, batchNo, expiry, null, qty, null, null));
    }

    private Long onlyBatchId(Long drugId) {
        return stockQuery.availableBatches(drugId, TODAY).get(0).batchId();
    }
}
