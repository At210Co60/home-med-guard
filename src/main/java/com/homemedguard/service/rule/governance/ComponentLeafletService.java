package com.homemedguard.service.rule.governance;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.error.BizException;
import com.homemedguard.service.rule.RuleModule;
import com.homemedguard.service.rule.entity.ComponentDictEntity;
import com.homemedguard.service.rule.entity.DrugLeafletEntity;
import com.homemedguard.service.rule.mapper.ComponentDictMapper;
import com.homemedguard.service.rule.mapper.DrugLeafletMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 说明书要点（② 拥有）。风险报告里的"应急指引"只取 OVERDOSE_ACTION 与 SEE_DOCTOR 两类，
 * 命中 A 级风险时随报告一起下发，前端才能给出"立即就医"的强提示。
 */
@RuleModule
@Service
public class ComponentLeafletService {

    public record LeafletView(Long componentId, String componentCode, String sectionCode, String content,
                              String sourceRef, String sourceDate) {
    }

    /** 章节白名单：写错的 code 一律拒绝，避免自由文本把要点表变成垃圾场。 */
    private static final Set<String> SECTIONS = Set.of(
            "CONTRAINDICATION", "PREGNANT", "ADVERSE", "INTERACTION", "OVERDOSE_ACTION", "SEE_DOCTOR");
    private static final Set<String> EMERGENCY_SECTIONS = Set.of("OVERDOSE_ACTION", "SEE_DOCTOR");

    private final DrugLeafletMapper leafletMapper;
    private final ComponentDictMapper componentDictMapper;

    public ComponentLeafletService(DrugLeafletMapper leafletMapper, ComponentDictMapper componentDictMapper) {
        this.leafletMapper = leafletMapper;
        this.componentDictMapper = componentDictMapper;
    }

    @Transactional
    public DrugLeafletEntity upsert(String componentCode, String sectionCode, String content,
                                    String sourceRef, LocalDate sourceDate) {
        ComponentDictEntity component = componentDictMapper.selectOne(Wrappers.<ComponentDictEntity>lambdaQuery()
                .eq(ComponentDictEntity::getComponentCode, upper(componentCode))
                .last("limit 1"));
        if (component == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "成分不存在：" + componentCode);
        }
        String section = requireSection(sectionCode);
        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "要点内容不能为空");
        }
        if (sourceRef == null || sourceRef.isBlank() || sourceDate == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "要点必须带来源与核对日期");
        }

        DrugLeafletEntity entity = leafletMapper.selectOne(Wrappers.<DrugLeafletEntity>lambdaQuery()
                .eq(DrugLeafletEntity::getComponentId, component.getId())
                .eq(DrugLeafletEntity::getSectionCode, section)
                .last("limit 1"));
        if (entity == null) {
            entity = new DrugLeafletEntity();
            entity.setComponentId(component.getId());
            entity.setSectionCode(section);
        }
        entity.setContent(content.trim());
        entity.setSourceRef(sourceRef.trim());
        entity.setSourceDate(sourceDate);
        if (entity.getId() == null) {
            leafletMapper.insert(entity);
        } else {
            leafletMapper.updateById(entity);
        }
        return entity;
    }

    public List<LeafletView> listByComponent(String componentCode) {
        ComponentDictEntity component = componentDictMapper.selectOne(Wrappers.<ComponentDictEntity>lambdaQuery()
                .eq(ComponentDictEntity::getComponentCode, upper(componentCode))
                .last("limit 1"));
        if (component == null) {
            return List.of();
        }
        return toViews(leafletMapper.selectList(Wrappers.<DrugLeafletEntity>lambdaQuery()
                .eq(DrugLeafletEntity::getComponentId, component.getId())
                .orderByAsc(DrugLeafletEntity::getSectionCode)), Map.of(component.getId(), component.getComponentCode()));
    }

    /** 命中风险后附带的应急指引：只按涉及的标准成分码取。 */
    public List<LeafletView> emergencyGuides(Collection<String> componentCodes) {
        if (componentCodes == null || componentCodes.isEmpty()) {
            return List.of();
        }
        List<ComponentDictEntity> components = componentDictMapper.selectList(
                Wrappers.<ComponentDictEntity>lambdaQuery()
                        .in(ComponentDictEntity::getComponentCode, componentCodes.stream().map(ComponentLeafletService::upper).toList()));
        if (components.isEmpty()) {
            return List.of();
        }
        Map<Long, String> codeById = new HashMap<>();
        for (ComponentDictEntity component : components) {
            codeById.put(component.getId(), component.getComponentCode());
        }
        List<DrugLeafletEntity> rows = leafletMapper.selectList(Wrappers.<DrugLeafletEntity>lambdaQuery()
                .in(DrugLeafletEntity::getComponentId, codeById.keySet())
                .in(DrugLeafletEntity::getSectionCode, EMERGENCY_SECTIONS)
                .orderByAsc(DrugLeafletEntity::getSectionCode));
        return toViews(rows, codeById);
    }

    private static List<LeafletView> toViews(List<DrugLeafletEntity> rows, Map<Long, String> codeById) {
        List<LeafletView> views = new ArrayList<>();
        for (DrugLeafletEntity row : rows) {
            views.add(new LeafletView(row.getComponentId(), codeById.get(row.getComponentId()), row.getSectionCode(),
                    row.getContent(), row.getSourceRef(),
                    row.getSourceDate() == null ? null : row.getSourceDate().toString()));
        }
        return views;
    }

    private static String requireSection(String sectionCode) {
        String section = upper(sectionCode);
        if (!SECTIONS.contains(section)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "sectionCode 只能是 " + String.join("/", SECTIONS));
        }
        return section;
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
