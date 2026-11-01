package com.homemedguard.service.rule.governance;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.error.BizException;
import com.homemedguard.service.rule.RuleModule;
import com.homemedguard.service.rule.core.RuleSetProvider;
import com.homemedguard.service.rule.entity.ComponentAliasEntity;
import com.homemedguard.service.rule.entity.ComponentDictEntity;
import com.homemedguard.service.rule.mapper.ComponentAliasMapper;
import com.homemedguard.service.rule.mapper.ComponentDictMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 后台维护标准成分与别名（drug_component 的归一化词表就在这里）。
 * 每次改动都让规则快照失效，否则最长 10 分钟才生效，管理员会以为没保存上。
 */
@RuleModule
@Service
public class ComponentAdminService {

    public record NewComponent(String componentCode, String standardName, String doseUnit, String therapeuticGroup) {
    }

    private final ComponentDictMapper componentDictMapper;
    private final ComponentAliasMapper componentAliasMapper;
    private final RuleSetProvider ruleSetProvider;

    public ComponentAdminService(ComponentDictMapper componentDictMapper,
                                 ComponentAliasMapper componentAliasMapper,
                                 RuleSetProvider ruleSetProvider) {
        this.componentDictMapper = componentDictMapper;
        this.componentAliasMapper = componentAliasMapper;
        this.ruleSetProvider = ruleSetProvider;
    }

    @Transactional
    public ComponentDictEntity createComponent(NewComponent form) {
        if (form.componentCode() == null || form.componentCode().isBlank()
                || form.standardName() == null || form.standardName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "componentCode 与 standardName 必填");
        }
        String code = form.componentCode().trim().toUpperCase(Locale.ROOT);
        if (componentDictMapper.exists(Wrappers.<ComponentDictEntity>lambdaQuery()
                .eq(ComponentDictEntity::getComponentCode, code))) {
            throw new BizException(ErrorCode.IDEMPOTENT_CONFLICT, "成分 " + code + " 已存在");
        }
        ComponentDictEntity entity = new ComponentDictEntity();
        entity.setComponentCode(code);
        entity.setStandardName(form.standardName().trim());
        entity.setDoseUnit(form.doseUnit() == null || form.doseUnit().isBlank() ? "mg"
                : form.doseUnit().trim().toLowerCase(Locale.ROOT));
        entity.setTherapeuticGroup(form.therapeuticGroup() == null ? null : form.therapeuticGroup().trim());
        componentDictMapper.insert(entity);

        addAliasInternal(entity.getId(), entity.getStandardName());
        ruleSetProvider.invalidate();
        return entity;
    }

    @Transactional
    public void addAlias(Long componentId, String alias) {
        if (alias == null || alias.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "别名不能为空");
        }
        if (componentDictMapper.selectById(componentId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "成分不存在：id=" + componentId);
        }
        addAliasInternal(componentId, alias.trim());
        ruleSetProvider.invalidate();
    }

    private void addAliasInternal(Long componentId, String alias) {
        String normalized = alias.trim().toLowerCase(Locale.ROOT);
        boolean exists = componentAliasMapper.exists(Wrappers.<ComponentAliasEntity>lambdaQuery()
                .eq(ComponentAliasEntity::getAlias, normalized));
        if (exists) {
            return;
        }
        ComponentAliasEntity entity = new ComponentAliasEntity();
        entity.setAlias(normalized);
        entity.setComponentId(componentId);
        componentAliasMapper.insert(entity);
    }
}
