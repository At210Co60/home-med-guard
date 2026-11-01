package com.homemedguard.service.drug.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.common.contract.DrugBasicDTO;
import com.homemedguard.common.contract.DrugQueryFacade;
import com.homemedguard.service.drug.DrugDictService;
import com.homemedguard.service.drug.DrugModule;
import com.homemedguard.service.drug.entity.DrugDictEntity;
import com.homemedguard.service.drug.mapper.DrugDictMapper;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** ②③ 读药品字典的唯一入口。只给基础字段，成分一律由 ② 自己按 drug_component 解析。 */
@DrugModule
public class DrugQueryFacadeImpl implements DrugQueryFacade {

    private final DrugDictMapper drugDictMapper;
    private final DrugDictService drugDictService;

    public DrugQueryFacadeImpl(DrugDictMapper drugDictMapper, DrugDictService drugDictService) {
        this.drugDictMapper = drugDictMapper;
        this.drugDictService = drugDictService;
    }

    @Override
    public Optional<DrugBasicDTO> drug(Long drugDictId) {
        if (drugDictId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(drugDictMapper.selectById(drugDictId)).map(DrugDictService::toDto);
    }

    @Override
    public List<DrugBasicDTO> drugs(Collection<Long> drugDictIds) {
        if (drugDictIds == null || drugDictIds.isEmpty()) {
            return List.of();
        }
        return drugDictMapper.selectList(Wrappers.<DrugDictEntity>lambdaQuery()
                        .in(DrugDictEntity::getId, drugDictIds)).stream()
                .map(DrugDictService::toDto)
                .toList();
    }
}
