package com.homemedguard.service.auth.core;

import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.error.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 从安全上下文取当前登录用户。任何家庭级接口都先用它拿 userId，再让 ① 校验归属。 */
public final class SessionUser {

    private SessionUser() {
    }

    public static LoginUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser user)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return user;
    }

    public static Long requireId() {
        return require().userId();
    }
}
