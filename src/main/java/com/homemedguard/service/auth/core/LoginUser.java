package com.homemedguard.service.auth.core;

import java.io.Serializable;

/** 认证主体。家庭归属不在这里，避免 token 里塞可变的成员关系导致越权判断过期。 */
public record LoginUser(Long userId, String username, String role) implements Serializable {

    public String authority() {
        return "ROLE_" + role;
    }
}
