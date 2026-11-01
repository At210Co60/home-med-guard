package com.homemedguard.service.rule;

import com.homemedguard.common.contract.AccountErasureRequestedEvent;
import com.homemedguard.service.rule.core.RuleSetProvider;
import com.homemedguard.service.rule.mapper.DrugComponentMapper;
import com.homemedguard.service.rule.mapper.RiskEvaluationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
/**
 * 账号注销时清 ② 自己那两张表：drug_component（自建药归一化结果）与 risk_evaluation_log（评估留痕）。
 * ① 不直接删这里的行——表的归属唯一，删自己那份由本模块负责。
 */
@RuleModule
public class AccountErasureListener {

    private static final Logger log = LoggerFactory.getLogger(AccountErasureListener.class);

    private final DrugComponentMapper drugComponentMapper;
    private final RiskEvaluationLogMapper riskEvaluationLogMapper;
    private final RuleSetProvider ruleSetProvider;

    public AccountErasureListener(DrugComponentMapper drugComponentMapper,
                                  RiskEvaluationLogMapper riskEvaluationLogMapper,
                                  RuleSetProvider ruleSetProvider) {
        this.drugComponentMapper = drugComponentMapper;
        this.riskEvaluationLogMapper = riskEvaluationLogMapper;
        this.ruleSetProvider = ruleSetProvider;
    }

    @EventListener
    @Transactional
    public void onAccountErasure(AccountErasureRequestedEvent event) {
        int components = 0;
        for (Long drugDictId : event.drugDictIds()) {
            components += drugComponentMapper.eraseByDrugDict(drugDictId);
        }

        int logs;
        if (event.familyDeleted() && event.familyId() != null) {
            logs = riskEvaluationLogMapper.eraseByFamily(event.familyId());
        } else {
            logs = 0;
            for (Long memberId : event.memberIds()) {
                logs += riskEvaluationLogMapper.eraseByMember(memberId);
            }
        }

        ruleSetProvider.invalidate();
        log.info("账号 {} 注销：② 清除 drug_component {} 行、risk_evaluation_log {} 行",
                event.userId(), components, logs);
    }
}
