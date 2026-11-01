package com.homemedguard.service.rule.core;

/** 命中来源，前端按此分组展示。 */
public enum RiskRuleKind {

    INTERACTION("药-药/药-食/药-酒相互作用"),
    CONTRAINDICATION("禁忌人群"),
    DOSE_LIMIT("重复成分剂量叠加"),
    SPACING("需间隔服用");

    private final String label;

    RiskRuleKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
