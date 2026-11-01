package com.homemedguard.service.rule.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 求值上下文。表达式可用的变量就这一份，写在文档里、不要随意加：
 * ageYears / weightKg / tags / targets / liverGrade / renalEgfr / doseA / doseB
 *
 * @param dailyDoseMg          标准成分码 → 每日总摄入量（mg），重复成分叠加后的结果
 * @param targets              非药品目标：ALCOHOL、GRAPEFRUIT_JUICE 等，药食/药酒规则按此匹配
 * @param unresolvedComponents 归一化失败的原始写法，必须回传给前端提示"该药未纳入规则库"
 */
public record RiskContext(
        Long familyId,
        Long memberId,
        Double ageYears,
        BigDecimal weightKg,
        Set<String> tagCodes,
        Set<String> allergyComponents,
        String liverGrade,
        Integer renalEgfr,
        Map<String, BigDecimal> dailyDoseMg,
        Set<String> targets,
        List<String> unresolvedComponents) {

    public RiskContext {
        tagCodes = tagCodes == null ? Set.of() : Set.copyOf(tagCodes);
        allergyComponents = allergyComponents == null ? Set.of() : Set.copyOf(allergyComponents);
        dailyDoseMg = dailyDoseMg == null ? Map.of() : new TreeMap<>(dailyDoseMg);
        targets = targets == null ? Set.of() : Set.copyOf(targets);
        unresolvedComponents = unresolvedComponents == null ? List.of() : List.copyOf(unresolvedComponents);
    }

    public Set<String> components() {
        return dailyDoseMg.keySet();
    }

    public BigDecimal doseOf(String componentCode) {
        return dailyDoseMg.getOrDefault(componentCode, BigDecimal.ZERO);
    }

    /** 每条规则的附加条件都在这个 env 上求值；doseA/doseB 由 RuleEvaluator 按规则两侧成分填入。 */
    public Map<String, Object> expressionEnv() {
        Map<String, Object> env = new HashMap<>();
        env.put("ageYears", ageYears == null ? -1D : ageYears);
        env.put("weightKg", weightKg == null ? -1D : weightKg.doubleValue());
        env.put("tags", new ArrayList<>(tagCodes));
        env.put("allergies", new ArrayList<>(allergyComponents));
        env.put("targets", new ArrayList<>(targets));
        env.put("liverGrade", liverGrade == null ? "" : liverGrade);
        env.put("renalEgfr", renalEgfr == null ? -1 : renalEgfr);
        return env;
    }
}
