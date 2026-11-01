package com.homemedguard.service.rule.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.contract.MemberContextDTO;
import com.homemedguard.common.contract.MemberProfileQueryFacade;
import com.homemedguard.common.error.BizException;
import com.homemedguard.common.trace.TraceId;
import com.homemedguard.service.rule.RuleModule;
import com.homemedguard.service.rule.api.RiskEvaluateFacade;
import com.homemedguard.service.rule.core.ContextBuilder;
import com.homemedguard.service.rule.core.EvaluationOutcome;
import com.homemedguard.service.rule.core.RiskContext;
import com.homemedguard.service.rule.core.RuleEvaluator;
import com.homemedguard.service.rule.core.RuleSet;
import com.homemedguard.service.rule.core.RuleSetProvider;
import com.homemedguard.service.rule.entity.RiskEvaluationLogEntity;
import com.homemedguard.service.rule.mapper.RiskEvaluationLogMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评估入口。成员上下文一律通过 ① 的契约读取，读不到就明确失败——
 * 禁忌人群和按公斤剂量都依赖档案，缺档案还给"无冲突"是危险的假阴性。
 */
@RuleModule
public class RiskEvaluateFacadeImpl implements RiskEvaluateFacade {

    private final RuleSetProvider ruleSetProvider;
    private final ContextBuilder contextBuilder;
    private final RuleEvaluator evaluator;
    private final ObjectProvider<MemberProfileQueryFacade> memberProfiles;
    private final RiskEvaluationLogMapper evaluationLogMapper;
    private final ObjectMapper objectMapper;

    public RiskEvaluateFacadeImpl(RuleSetProvider ruleSetProvider,
                                  ContextBuilder contextBuilder,
                                  RuleEvaluator evaluator,
                                  ObjectProvider<MemberProfileQueryFacade> memberProfiles,
                                  RiskEvaluationLogMapper evaluationLogMapper,
                                  ObjectMapper objectMapper) {
        this.ruleSetProvider = ruleSetProvider;
        this.contextBuilder = contextBuilder;
        this.evaluator = evaluator;
        this.memberProfiles = memberProfiles;
        this.evaluationLogMapper = evaluationLogMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public Report evaluate(Request request) {
        if (request == null || request.memberId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "memberId 必填");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "items 至少一条：在服方案或拟新增药");
        }

        MemberProfileQueryFacade profiles = memberProfiles.getIfAvailable();
        if (profiles == null) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "① 成员档案模块未接入，禁忌人群与剂量上限无法判定，本次不给出评估结论");
        }
        MemberContextDTO member = profiles.memberContext(request.memberId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "成员不存在：memberId=" + request.memberId()));

        RuleSet rules = ruleSetProvider.current();
        RiskContext context = contextBuilder.build(member, request, rules, LocalDate.now());
        EvaluationOutcome outcome = evaluator.evaluate(context, rules);

        writeTrace(context, request, outcome);
        return toReport(outcome);
    }

    private Report toReport(EvaluationOutcome outcome) {
        List<RiskItem> items = outcome.hits().stream()
                .map(hit -> new RiskItem(hit.kind().label(), hit.ruleCode(), hit.ruleVersion(),
                        hit.severity().name(), hit.severity().label(), hit.severity().blocking(),
                        hit.components(), hit.mechanism(), hit.advice(), hit.basis(), hit.evidence(),
                        hit.sourceRef(), hit.sourceDate() == null ? null : hit.sourceDate().toString()))
                .toList();
        return new Report(TraceId.current(), outcome.ruleSetStamp(),
                outcome.maxSeverity() == null ? null : outcome.maxSeverity().name(),
                outcome.blocking(),
                items, outcome.failedRuleCodes(), outcome.incompleteReasons(), DISCLAIMER);
    }

    /** 留痕只用于追溯和报表，写失败不能影响判定结果本身。 */
    private void writeTrace(RiskContext context, Request request, EvaluationOutcome outcome) {
        RiskEvaluationLogEntity entity = new RiskEvaluationLogEntity();
        entity.setTraceId(TraceId.current());
        entity.setFamilyId(context.familyId());
        entity.setMemberId(context.memberId());
        entity.setRuleSetStamp(outcome.ruleSetStamp());
        entity.setScenario(request.scenario() == null ? "REGIMEN_ADD" : request.scenario());
        entity.setHitCount(outcome.hits().size());
        entity.setMaxSeverity(outcome.maxSeverity() == null ? null : outcome.maxSeverity().name());
        entity.setInputSnapshot(json(inputSnapshot(context)));
        entity.setHitsJson(outcome.hits().isEmpty() ? null : json(outcome.hits()));
        entity.setFailedRuleCount(outcome.failedRuleCodes().size());
        entity.setEvaluatedAt(LocalDateTime.now());
        evaluationLogMapper.insert(entity);
    }

    private Map<String, Object> inputSnapshot(RiskContext context) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ageYears", context.ageYears());
        snapshot.put("weightKg", context.weightKg());
        snapshot.put("tags", context.tagCodes());
        snapshot.put("allergies", context.allergyComponents());
        snapshot.put("targets", context.targets());
        snapshot.put("dailyDoseMg", context.dailyDoseMg());
        snapshot.put("unresolved", context.unresolvedComponents());
        return snapshot;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "评估快照序列化失败");
        }
    }
}
