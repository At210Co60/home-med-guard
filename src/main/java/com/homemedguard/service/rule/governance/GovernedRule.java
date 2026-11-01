package com.homemedguard.service.rule.governance;

import java.time.LocalDate;

/** 三张规则表共有的"可治理"字段，让审核流只写一遍。 */
public interface GovernedRule {

    Long getId();

    String getRuleCode();

    Integer getVersion();

    String getStatus();

    void setStatus(String status);

    LocalDate getEffectiveFrom();

    void setEffectiveFrom(LocalDate effectiveFrom);

    String getSourceRef();

    void setSourceRef(String sourceRef);

    LocalDate getSourceDate();

    void setSourceDate(LocalDate sourceDate);
}
