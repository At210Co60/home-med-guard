package com.homemedguard.service.rule.governance;

/**
 * 规则状态。流转只允许：DRAFT →(submit) PENDING →(approve) PUBLISHED →(retire) RETIRED，
 * PENDING →(reject) 回 DRAFT。已发布的版本不可原地改，只能开新版本。
 */
public final class RuleStatus {

    public static final String DRAFT = "DRAFT";
    public static final String PENDING = "PENDING";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String RETIRED = "RETIRED";

    private RuleStatus() {
    }
}
