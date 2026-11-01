package com.homemedguard.web;

import com.homemedguard.common.api.Result;
import com.homemedguard.service.auth.AuthService;
import com.homemedguard.service.auth.entity.SysUserEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "01-账号")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterForm(String username, String password, String nickname) {
    }

    public record LoginForm(String username, String password) {
    }

    public record RefreshForm(String refreshToken) {
    }

    public record PublicUser(Long id, String username, String nickname, String role) {
    }

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "注册（同时自动创建个人家庭）")
    @PostMapping("/register")
    public Result<PublicUser> register(@RequestBody RegisterForm form) {
        SysUserEntity user = authService.register(form.username(), form.password(), form.nickname());
        return Result.ok(new PublicUser(user.getId(), user.getUsername(), user.getNickname(), user.getRole()));
    }

    @Operation(summary = "登录，返回 access token 与 refresh token")
    @PostMapping("/login")
    public Result<AuthService.TokenPair> login(@RequestBody LoginForm form) {
        return Result.ok(authService.login(form.username(), form.password()));
    }

    @Operation(summary = "换一组新 token，旧的 refresh token 立即作废")
    @PostMapping("/refresh")
    public Result<AuthService.TokenPair> refresh(@RequestBody RefreshForm form) {
        return Result.ok(authService.refresh(form.refreshToken()));
    }

    @Operation(summary = "登出：吊销本次 refresh token")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody RefreshForm form) {
        authService.logout(form.refreshToken());
        return Result.ok();
    }
}
