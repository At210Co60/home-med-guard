package com.homemedguard.service.drug;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.contract.DrugBasicDTO;
import com.homemedguard.common.contract.DrugQueryFacade;
import com.homemedguard.common.contract.DrugRegisteredEvent;
import com.homemedguard.common.error.BizException;
import com.homemedguard.config.CacheConfig;
import com.homemedguard.service.drug.entity.DrugBarcodeEntity;
import com.homemedguard.service.drug.entity.DrugDictEntity;
import com.homemedguard.service.drug.mapper.DrugBarcodeMapper;
import com.homemedguard.service.drug.mapper.DrugDictMapper;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 药品字典：录入、补全、条码映射。
 * 新增药品只发事件，成分归一化由 ② 监听后写自己的 drug_component——① 不碰那张表。
 */
@DrugModule
@Service
public class DrugDictService {

    private static final int SUGGEST_LIMIT = 10;

    private final DrugDictMapper drugDictMapper;
    private final DrugBarcodeMapper barcodeMapper;
    private final DrugSuggestQuery suggestQuery;
    private final ApplicationEventPublisher events;
    private final CacheManager cacheManager;

    public DrugDictService(DrugDictMapper drugDictMapper,
                           DrugBarcodeMapper barcodeMapper,
                           DrugSuggestQuery suggestQuery,
                           ApplicationEventPublisher events,
                           CacheManager cacheManager) {
        this.drugDictMapper = drugDictMapper;
        this.barcodeMapper = barcodeMapper;
        this.suggestQuery = suggestQuery;
        this.events = events;
        this.cacheManager = cacheManager;
    }

    public List<DrugBasicDTO> suggest(String keyword, Long userId, boolean sysAdmin) {
        return suggestQuery.byKeyword(keyword).stream()
                .filter(drug -> drug.visibleTo(userId, sysAdmin))
                .limit(SUGGEST_LIMIT)
                .map(DrugDictService::toDto)
                .toList();
    }

    public DrugDictEntity requireVisible(Long drugDictId, Long userId, boolean sysAdmin) {
        DrugDictEntity drug = drugDictMapper.selectById(drugDictId);
        if (drug == null || !drug.visibleTo(userId, sysAdmin)) {
            throw new BizException(ErrorCode.NOT_FOUND, "药品不存在或无权访问");
        }
        return drug;
    }

    @Transactional
    public DrugDictEntity create(Long userId, DrugSpec spec, boolean sysAdmin) {
        if (spec.genericName() == null || spec.genericName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "通用名必填");
        }
        DrugDictEntity drug = new DrugDictEntity();
        drug.setGenericName(spec.genericName().trim());
        drug.setTradeName(trim(spec.tradeName()));
        drug.setDosageForm(trim(spec.dosageForm()));
        drug.setSpecification(trim(spec.specification()));
        drug.setManufacturer(trim(spec.manufacturer()));
        drug.setApprovalNo(trim(spec.approvalNo()));
        drug.setOtcRx("RX".equalsIgnoreCase(spec.otcRx()) ? "RX" : "OTC");
        drug.setSource(sysAdmin ? "DICT" : "USER_INPUT");
        drug.setOwnerUserId(sysAdmin ? null : userId);
        drugDictMapper.insert(drug);

        events.publishEvent(new DrugRegisteredEvent(drug.getId(), null, drug.getGenericName(),
                drug.getTradeName(), drug.getDosageForm(), drug.getSource()));
        evictSuggestCache();
        return drug;
    }

    public Optional<DrugDictEntity> resolveBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return Optional.empty();
        }
        DrugBarcodeEntity mapping = barcodeMapper.selectOne(Wrappers.<DrugBarcodeEntity>lambdaQuery()
                .eq(DrugBarcodeEntity::getBarcode, barcode.trim())
                .last("limit 1"));
        return mapping == null ? Optional.empty() : Optional.ofNullable(drugDictMapper.selectById(mapping.getDrugDictId()));
    }

    @Transactional
    public void bindBarcode(String barcode, Long drugDictId) {
        if (barcode == null || barcode.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "条码不能为空");
        }
        DrugDictEntity drug = requireExists(drugDictId);
        DrugBarcodeEntity existing = new DrugBarcodeEntity();
        existing.setBarcode(barcode.trim());
        existing.setDrugDictId(drug.getId());
        if (barcodeMapper.selectCount(Wrappers.<DrugBarcodeEntity>lambdaQuery()
                .eq(DrugBarcodeEntity::getBarcode, barcode.trim())) > 0) {
            throw new BizException(ErrorCode.IDEMPOTENT_CONFLICT, "该条码已经绑定过药品");
        }
        barcodeMapper.insert(existing);
    }

    private DrugDictEntity requireExists(Long drugDictId) {
        DrugDictEntity drug = drugDictMapper.selectById(drugDictId);
        if (drug == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "药品不存在");
        }
        return drug;
    }

    public static DrugBasicDTO toDto(DrugDictEntity drug) {
        return new DrugBasicDTO(drug.getId(), drug.getGenericName(), drug.getTradeName(), drug.getDosageForm(),
                drug.getSpecification(), drug.getApprovalNo(), drug.getOtcRx());
    }

    private void evictSuggestCache() {
        var cache = cacheManager.getCache(CacheConfig.DRUG_DICT_SUGGEST);
        if (cache != null) {
            cache.clear();
        }
    }

    private static String trim(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
