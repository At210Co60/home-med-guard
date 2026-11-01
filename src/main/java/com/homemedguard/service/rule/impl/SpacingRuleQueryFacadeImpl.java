package com.homemedguard.service.rule.impl;

import com.homemedguard.service.rule.RuleModule;
import com.homemedguard.service.rule.api.SpacingRuleQueryFacade;
import com.homemedguard.service.rule.core.RuleSet;
import com.homemedguard.service.rule.core.RuleSetProvider;
import com.homemedguard.service.rule.entity.SpacingRuleEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 给 ③ 排程求解器供数。成分对无序，多条命中取最小间隔最大（最严）的那条。
 */
@RuleModule
public class SpacingRuleQueryFacadeImpl implements SpacingRuleQueryFacade {

    private final RuleSetProvider ruleSetProvider;

    public SpacingRuleQueryFacadeImpl(RuleSetProvider ruleSetProvider) {
        this.ruleSetProvider = ruleSetProvider;
    }

    @Override
    public Optional<SpacingRequirement> minGapBetween(String componentA, String componentB) {
        if (componentA == null || componentB == null || componentA.isBlank() || componentB.isBlank()) {
            return Optional.empty();
        }
        RuleSet rules = ruleSetProvider.current();
        return rules.spacings().stream()
                .filter(row -> matches(row, componentA, componentB))
                .max(Comparator.comparingInt(SpacingRuleEntity::getMinGapMinutes))
                .map(SpacingRuleQueryFacadeImpl::toRequirement);
    }

    @Override
    public List<SpacingRequirement> gapsWithin(List<String> componentCodes) {
        if (componentCodes == null || componentCodes.size() < 2) {
            return List.of();
        }
        List<SpacingRequirement> found = new ArrayList<>();
        for (int i = 0; i < componentCodes.size(); i++) {
            for (int j = i + 1; j < componentCodes.size(); j++) {
                minGapBetween(componentCodes.get(i), componentCodes.get(j)).ifPresent(found::add);
            }
        }
        return found;
    }

    private static boolean matches(SpacingRuleEntity row, String a, String b) {
        return pairEquals(row.getComponentA(), row.getComponentB(), a, b);
    }

    private static boolean pairEquals(String leftA, String leftB, String rightA, String rightB) {
        return (same(leftA, rightA) && same(leftB, rightB)) || (same(leftA, rightB) && same(leftB, rightA));
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right.trim());
    }

    private static SpacingRequirement toRequirement(SpacingRuleEntity row) {
        return new SpacingRequirement(row.getComponentA().toUpperCase(Locale.ROOT),
                row.getComponentB().toUpperCase(Locale.ROOT), row.getMinGapMinutes(), row.getReason(),
                row.getRuleCode(), row.getVersion());
    }
}
