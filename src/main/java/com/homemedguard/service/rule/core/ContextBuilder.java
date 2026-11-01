package com.homemedguard.service.rule.core;

import com.homemedguard.common.contract.MemberContextDTO;
import com.homemedguard.service.rule.RuleModule;
import com.homemedguard.service.rule.api.RiskEvaluateFacade.DoseInput;
import com.homemedguard.service.rule.api.RiskEvaluateFacade.Request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 把"成员档案 + 在服/拟新增"折成求值上下文：成分归一化、按剂型含量拆回每味成分的每日 mg、重复成分叠加。
 * 这一步是全模块最容易算错的地方（复方、单位、频次），所以单独成类、单独测。
 */
@RuleModule
public class ContextBuilder {

    public RiskContext build(MemberContextDTO member, Request request, RuleSet rules, LocalDate today) {        Map<String, BigDecimal> doses = new TreeMap<>();
        List<String> unresolved = new ArrayList<>();

        for (DoseInput item : request.items()) {
            int times = item.timesPerDay() == null || item.timesPerDay() < 1 ? 1 : item.timesPerDay();
            if (item.drugDictId() != null) {
                fromDrug(rules, item, times, doses, unresolved);
            } else if (item.componentRef() != null && !item.componentRef().isBlank()) {
                fromComponent(rules, item, times, doses, unresolved);
            } else {
                unresolved.add("有一条录入既没有 drugDictId 也没有成分名，已跳过");
            }
        }

        Double ageYears = member.birthDate() == null ? null
                : Double.valueOf(ChronoUnit.YEARS.between(member.birthDate(), today));
        Set<String> tags = derivedTags(member, ageYears);

        return new RiskContext(member.familyId(), member.memberId(), ageYears, member.weightKg(),
                tags, member.allergyComponentCodes(), member.liverGrade(), member.renalEgfr(), doses,
                request.targets() == null ? Set.of() : Set.copyOf(request.targets()), unresolved);
    }

    private void fromDrug(RuleSet rules, DoseInput item, int times, Map<String, BigDecimal> doses, List<String> unresolved) {
        List<RuleSet.DrugComponentView> views = rules.componentsByDrug().get(item.drugDictId());
        if (views == null || views.isEmpty()) {
            unresolved.add("drug#" + item.drugDictId() + " 尚未归一化到标准成分，未纳入叠加计算");
            return;
        }
        if (item.doseAmount() == null) {
            unresolved.add("drug#" + item.drugDictId() + " 缺少每次用量（片/粒/mL 数）");
            return;
        }
        for (RuleSet.DrugComponentView view : views) {
            if (!"CONFIRMED".equalsIgnoreCase(view.resolveStatus())) {
                unresolved.add(view.componentCode() + " 的成分含量待人工确认，结果可能不完整");
            }
            BigDecimal perFormMg = UnitConverter.toMilligrams(view.contentPerForm(), rules.unitOf(view.componentCode()));
            accumulate(doses, view.componentCode(), perFormMg.multiply(item.doseAmount()).multiply(BigDecimal.valueOf(times)));
        }
    }

    private void fromComponent(RuleSet rules, DoseInput item, int times, Map<String, BigDecimal> doses, List<String> unresolved) {
        String code = rules.normalize(item.componentRef()).orElse(null);
        if (code == null) {
            unresolved.add("“" + item.componentRef() + "” 不在成分库内，规则无法命中（未识别不等于安全）");
            return;
        }
        if (item.doseAmount() == null) {
            unresolved.add(code + " 缺少单次剂量，未做叠加与上限校验");
            return;
        }
        BigDecimal perDoseMg = UnitConverter.toMilligrams(item.doseAmount(), item.doseUnit());
        accumulate(doses, code, perDoseMg.multiply(BigDecimal.valueOf(times)));
    }

    private static void accumulate(Map<String, BigDecimal> doses, String code, BigDecimal mg) {
        doses.merge(code, mg, BigDecimal::add);
    }

    /**
     * 标签从档案标签 + 数值派生：① 只存出生日/肝肾功能分级，是否算"儿童""肾功能不全"由 ② 判定，
     * 这样规则里的 tag_code 才有一致的口径。阈值写死在这里并配单测，改口径必须动测试。
     */
    private static Set<String> derivedTags(MemberContextDTO member, Double ageYears) {
        Set<String> tags = new HashSet<>(member.tagCodes());
        if (ageYears != null) {
            if (ageYears < 12) {
                tags.add("CHILD");
            }
            if (ageYears >= 65) {
                tags.add("ELDERLY");
            }
        }
        if ("B".equalsIgnoreCase(member.liverGrade()) || "C".equalsIgnoreCase(member.liverGrade())) {
            tags.add("HEPATIC_IMPAIRED");
        }
        if (member.renalEgfr() != null) {
            if (member.renalEgfr() < 60) {
                tags.add("RENAL_IMPAIRED");
            }
            if (member.renalEgfr() < 30) {
                tags.add("RENAL_SEVERE");
            }
        }
        return tags;
    }
}
