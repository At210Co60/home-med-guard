package com.homemedguard.common.contract;

import java.util.List;
import java.util.Optional;

/**
 * ① 家庭与药箱底座对外暴露的只读契约。
 * ②③ 只能经此取成员上下文，禁止注入 ① 的 Mapper、禁止 JOIN family_member_profile。
 */
public interface MemberProfileQueryFacade {

    Optional<MemberContextDTO> memberContext(Long memberId);

    /** 家庭全体在册成员，用于"给整个药箱做一次冲突体检"。 */
    List<MemberContextDTO> familyMemberContexts(Long familyId);
}
