package com.homemedguard.service.stock;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.contract.DrugBasicDTO;
import com.homemedguard.common.contract.DrugQueryFacade;
import com.homemedguard.common.error.BizException;
import com.homemedguard.service.stock.entity.FamilyDrugEntity;
import com.homemedguard.service.stock.mapper.FamilyDrugMapper;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 家庭在册药品登记。字典能查到时以字典为准，查不到就退回录入快照——
 * ① 的药品字典此刻还没有实现，所以 DrugQueryFacade 走 ObjectProvider 可选注入，
 * 假数据只进测试，src/main 不放 stub（否则 A 的真实现推上来还要我们来删）。
 */
@StockModule
public class FamilyDrugService {

    /**
     * @param drugDictId 取自标准字典时非空；完全自建药传 null
     * @param unit       缺省 PCS
     */
    public record RegisterCommand(Long familyId,
                                  Long drugDictId,
                                  String genericName,
                                  String tradeName,
                                  String dosageForm,
                                  String specification,
                                  String approvalNo,
                                  String otcRx,
                                  String unit,
                                  Integer minSafeQty) {
    }

    private final FamilyDrugMapper drugs;
    private final ObjectProvider<DrugQueryFacade> drugDict;

    public FamilyDrugService(FamilyDrugMapper drugs, ObjectProvider<DrugQueryFacade> drugDict) {
        this.drugs = drugs;
        this.drugDict = drugDict;
    }

    /** 同一家庭重复登记同一个字典药，返回已有记录而不是再来一条。 */
    public FamilyDrugEntity register(RegisterCommand cmd) {
        if (cmd == null || cmd.familyId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "familyId 不能为空");
        }
        if (cmd.drugDictId() != null) {
            FamilyDrugEntity existing = drugs.selectOne(Wrappers.<FamilyDrugEntity>lambdaQuery()
                    .eq(FamilyDrugEntity::getFamilyId, cmd.familyId())
                    .eq(FamilyDrugEntity::getDrugDictId, cmd.drugDictId())
                    .last("limit 1"));
            if (existing != null) {
                return existing;
            }
        }

        FamilyDrugEntity entity = new FamilyDrugEntity();
        entity.setFamilyId(cmd.familyId());
        entity.setDrugDictId(cmd.drugDictId());
        entity.setSource(cmd.drugDictId() == null
                ? FamilyDrugEntity.SOURCE_USER_INPUT : FamilyDrugEntity.SOURCE_DICT);
        entity.setGenericName(cmd.genericName());
        entity.setTradeName(cmd.tradeName());
        entity.setDosageForm(cmd.dosageForm());
        entity.setSpecification(cmd.specification());
        entity.setApprovalNo(cmd.approvalNo());
        entity.setOtcRx(cmd.otcRx());
        entity.setUnit(cmd.unit() == null || cmd.unit().isBlank() ? "PCS" : cmd.unit());
        entity.setMinSafeQty(cmd.minSafeQty() == null ? 0 : cmd.minSafeQty());

        if (cmd.drugDictId() != null) {
            enrichFromDict(entity);
        }
        if (entity.getGenericName() == null || entity.getGenericName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "通用名不能为空：字典查不到时必须手工填写");
        }
        drugs.insert(entity);
        return entity;
    }

    private void enrichFromDict(FamilyDrugEntity entity) {
        DrugQueryFacade facade = drugDict.getIfAvailable();
        if (facade == null) {
            // 字典模块没接线：允许带快照继续，只是来源锚点暂时查不到详情，不阻断入库
            return;
        }
        DrugBasicDTO dict = facade.drug(entity.getDrugDictId())
                .orElseThrow(() -> new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                        "药品字典里没有编号 " + entity.getDrugDictId() + "，请改用自建药或核对编号"));
        entity.setGenericName(dict.genericName());
        entity.setTradeName(dict.tradeName());
        entity.setDosageForm(dict.dosageForm());
        entity.setSpecification(dict.specification());
        entity.setApprovalNo(dict.approvalNo());
        entity.setOtcRx(dict.otcRx());
    }
}
