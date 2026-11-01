package com.homemedguard.common.access;

import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.error.BizException;
import com.homemedguard.service.auth.core.LoginUser;
import com.homemedguard.service.family.FamilyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 归属判定的边界：宁可拒绝，也不能让一个登录用户读到别人家的药箱。 */
class FamilyAccessGuardTest {

    @ParameterizedTest(name = "本人家庭 {0}，请求 {1} -> {2}")
    @CsvSource({
            "100, 100, 100",
            "100, '', 100",
            "100, 200, FORBIDDEN",
            "'', 100, FORBIDDEN",
            "'', '', FORBIDDEN"
    })
    @DisplayName("请求里的 familyId 只做一致性校验，最终以登录态为准")
    void decidesFromSessionNotFromRequest(String own, String requested, String expected) {
        Long ownId = own.isEmpty() ? null : Long.valueOf(own);
        Long requestedId = requested.isEmpty() ? null : Long.valueOf(requested);

        if ("FORBIDDEN".equals(expected)) {
            assertThatThrownBy(() -> FamilyAccessGuard.decide(ownId, requestedId))
                    .isInstanceOf(BizException.class);
            return;
        }
        assertThat(FamilyAccessGuard.decide(ownId, requestedId)).isEqualTo(Long.valueOf(expected));
    }

    @Test
    @DisplayName("① 的 family 模块没接线时按请求值降级，但仍要求已登录")
    void degradesWhenFamilyModuleIsOff() {
        FamilyAccessGuard guard = new FamilyAccessGuard(familyModuleOff());
        loginAs(1L);
        try {
            assertThat(guard.requireFamilyId(9L)).isEqualTo(9L);
            assertThatThrownBy(() -> guard.requireFamilyId(null))
                    .isInstanceOf(BizException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("安全上下文为空 = 未登录，不放到任何家庭数据")
    void requiresAuthenticatedPrincipal() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> new FamilyAccessGuard(familyModuleOff()).requireFamilyId(9L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    /** ① 的 family 模块关掉时的 ObjectProvider 形状：拿不出实例，但不炸。 */
    private static ObjectProvider<FamilyService> familyModuleOff() {
        return new ObjectProvider<>() {
            @Override
            public FamilyService getObject() {
                throw new NoSuchBeanDefinitionException(FamilyService.class);
            }

            @Override
            public FamilyService getObject(Object... args) {
                throw new NoSuchBeanDefinitionException(FamilyService.class);
            }

            @Override
            public FamilyService getIfAvailable() {
                return null;
            }

            @Override
            public FamilyService getIfUnique() {
                return null;
            }
        };
    }

    private static void loginAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(userId, "teammate-b", "MEMBER"), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
