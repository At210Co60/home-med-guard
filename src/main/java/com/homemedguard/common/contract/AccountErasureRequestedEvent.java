package com.homemedguard.common.contract;

import java.util.List;

/**
 * 账号注销事件：① 删完自己那部分后广播，其它模块各自删自己拥有的表。
 * ① 不跨模块删 risk_evaluation_log / drug_component——那会违反"表的归属唯一"。
 */
public record AccountErasureRequestedEvent(
        Long userId,
        Long familyId,
        boolean familyDeleted,
        List<Long> memberIds,
        List<Long> drugDictIds) {

    public AccountErasureRequestedEvent {
        memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
        drugDictIds = drugDictIds == null ? List.of() : List.copyOf(drugDictIds);
    }
}
