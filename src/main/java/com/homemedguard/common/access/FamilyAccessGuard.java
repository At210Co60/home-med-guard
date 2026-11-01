package com.homemedguard.common.access;

import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.error.BizException;
import com.homemedguard.service.auth.core.SessionUser;
import com.homemedguard.service.family.FamilyService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 家庭归属守卫（技术设施，不含业务规则，所以放 common）。
 *
 * <p>存在的理由：/api/stock、/api/expiry、/api/report 都以 familyId 为入参，
 * 直接采信客户端给的 id 就是越权读取别人家药箱的口子。按 ① 在 SessionUser 上写的约定
 * ——先拿登录态 userId，再让 ① 校验归属——这里把两步收成一个入口，
 * 控制器只认它返回的 familyId，不再用请求里那个。
 *
 * <p>① 的 family 模块没启用时（app.module.family=false）无法校验归属：登录仍然是必须的，
 * 家庭维度退化成按请求值过滤，由 SecurityConfig 兜住未登录访问。
 */
@Component
public class FamilyAccessGuard {

    private final ObjectProvider<FamilyService> families;

    public FamilyAccessGuard(ObjectProvider<FamilyService> families) {
        this.families = families;
    }

    /**
     * @param requestedFamilyId 客户端传来的家庭 id，可为空
     * @return 本次请求允许操作的 familyId
     */
    public Long requireFamilyId(Long requestedFamilyId) {
        Long userId = SessionUser.requireId();
        FamilyService service = families.getIfAvailable();
        if (service == null) {
            if (requestedFamilyId == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "familyId 不能为空");
            }
            return requestedFamilyId;
        }
        Long mine = service.membershipOf(userId).map(member -> member.getFamilyId()).orElse(null);
        return decide(mine, requestedFamilyId);
    }

    /** 纯判定，便于表驱动测试：归属不明一律拒。 */
    public static Long decide(Long ownFamilyId, Long requestedFamilyId) {
        if (ownFamilyId == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前账号还没有家庭，无法操作药箱数据");
        }
        if (requestedFamilyId != null && !requestedFamilyId.equals(ownFamilyId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作自己家庭的数据");
        }
        return ownFamilyId;
    }
}
