package com.homemedguard.service.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.error.BizException;
import com.homemedguard.config.AuthProperties;
import com.homemedguard.service.auth.core.JwtService;
import com.homemedguard.service.auth.entity.RefreshTokenEntity;
import com.homemedguard.service.auth.entity.SysUserEntity;
import com.homemedguard.service.auth.mapper.RefreshTokenMapper;
import com.homemedguard.service.auth.mapper.SysUserMapper;
import com.homemedguard.service.family.FamilyService;
import com.homemedguard.service.family.MemberProfileService;
import com.homemedguard.service.family.ProfileSpec;
import com.homemedguard.service.family.entity.FamilyMemberEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Set;

/**
 * 注册/登录/刷新/登出。
 * 注册即建个人家庭（① 的口径）：② ③ 任何时刻都能拿到 familyId，不用先走一遍建组流程。
 * 家庭模块被关掉时（app.module.family=false）只建账号，其余功能照常——模块之间不硬绑。
 */
@Service
public class AuthService {

    public record TokenPair(String accessToken, String refreshToken, long accessTokenExpiresInSeconds) {
    }

    private static final String ROLE_MEMBER = "MEMBER";

    private final SysUserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProperties authProperties;
    private final ObjectProvider<FamilyService> familyServices;
    private final ObjectProvider<MemberProfileService> profileServices;
    private final SecureRandom random = new SecureRandom();

    public AuthService(SysUserMapper userMapper,
                       RefreshTokenMapper refreshTokenMapper,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthProperties authProperties,
                       ObjectProvider<FamilyService> familyServices,
                       ObjectProvider<MemberProfileService> profileServices) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authProperties = authProperties;
        this.familyServices = familyServices;
        this.profileServices = profileServices;
    }

    @Transactional
    public SysUserEntity register(String username, String rawPassword, String nickname) {
        String name = username == null ? "" : username.trim();
        if (!name.matches("^[A-Za-z0-9_]{3,32}$")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名只能是 3–32 位字母、数字或下划线");
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new BizException(ErrorCode.PARAM_INVALID, "密码至少 8 位");
        }
        if (existsByName(name)) {
            throw new BizException(ErrorCode.IDEMPOTENT_CONFLICT, "用户名已被注册");
        }

        SysUserEntity user = new SysUserEntity();
        user.setUsername(name);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname == null || nickname.isBlank() ? name : nickname.trim());
        user.setRole(ROLE_MEMBER);
        user.setStatus("ACTIVE");
        userMapper.insert(user);

        familyServices.ifAvailable(familyService -> {
            FamilyMemberEntity owner = familyService.createPersonalFamily(user.getId());
            profileServices.ifAvailable(profileService -> profileService.saveProfile(owner.getId(),
                    new ProfileSpec(user.getNickname(), null, null, null, Set.of(), null, null, null, null)));
        });
        return user;
    }

    @Transactional
    public TokenPair login(String username, String rawPassword) {
        SysUserEntity user = userMapper.selectOne(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getUsername, username == null ? "" : username.trim())
                .last("limit 1"));
        // 用户名不存在与密码错误给同一个文案，不暴露账号是否存在
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码不正确");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已停用");
        }
        return issue(user);
    }

    /** Refresh Token 旋转：旧 token 用一次即废，被偷也活不过一个周期。 */
    @Transactional
    public TokenPair refresh(String refreshToken) {
        RefreshTokenEntity stored = findValid(refreshToken);
        SysUserEntity user = userMapper.selectById(stored.getUserId());
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        stored.setRevoked(1);
        refreshTokenMapper.updateById(stored);
        return issue(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        RefreshTokenEntity stored = refreshTokenMapper.selectOne(Wrappers.<RefreshTokenEntity>lambdaQuery()
                .eq(RefreshTokenEntity::getTokenHash, sha256(refreshToken))
                .last("limit 1"));
        if (stored != null) {
            stored.setRevoked(1);
            refreshTokenMapper.updateById(stored);
        }
    }

    private TokenPair issue(SysUserEntity user) {
        byte[] raw = new byte[48];
        random.nextBytes(raw);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUserId(user.getId());
        entity.setTokenHash(sha256(refreshToken));
        entity.setExpiresAt(LocalDateTime.now().plus(authProperties.getRefreshTokenTtl()));
        entity.setRevoked(0);
        refreshTokenMapper.insert(entity);

        return new TokenPair(jwtService.issueAccessToken(user), refreshToken,
                authProperties.getAccessTokenTtl().toSeconds());
    }

    private RefreshTokenEntity findValid(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少 refresh token");
        }
        RefreshTokenEntity stored = refreshTokenMapper.selectOne(Wrappers.<RefreshTokenEntity>lambdaQuery()
                .eq(RefreshTokenEntity::getTokenHash, sha256(refreshToken))
                .last("limit 1"));
        if (stored == null || stored.getRevoked() == 1 || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        return stored;
    }

    private boolean existsByName(String username) {
        return userMapper.exists(Wrappers.<SysUserEntity>lambdaQuery().eq(SysUserEntity::getUsername, username));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
