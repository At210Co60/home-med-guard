package com.homemedguard.service.rule.core;

/** 规则快照来源。测试里给内存实现，生产给 DB+Caffeine 实现。 */
public interface RuleSetProvider {

    RuleSet current();

    /** 管理员发布/停用规则后必须调它，否则最长 10 分钟还在用旧规则集。 */
    void invalidate();
}
