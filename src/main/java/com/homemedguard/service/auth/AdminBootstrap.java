package com.homemedguard.service.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.config.AuthProperties;
import com.homemedguard.service.auth.entity.SysUserEntity;
import com.homemedguard.service.auth.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 产生第一个系统管理员。
 * 后台接口本身要求 SYS_ADMIN，没有引导入口就会死锁；配置为空就直接跳过，仓库里不留任何口令。
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String SYS_ADMIN = "SYS_ADMIN";

    private final AuthProperties authProperties;
    private final AuthService authService;
    private final SysUserMapper userMapper;

    public AdminBootstrap(AuthProperties authProperties, AuthService authService, SysUserMapper userMapper) {
        this.authProperties = authProperties;
        this.authService = authService;
        this.userMapper = userMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin bootstrap = authProperties.getBootstrap();
        if (bootstrap.getUsername() == null || bootstrap.getUsername().isBlank()) {
            return;
        }
        String username = bootstrap.getUsername().trim();
        SysUserEntity user = find(username);
        if (user == null) {
            authService.register(username, bootstrap.getPassword(), "系统管理员");
            user = find(username);
        }
        if (user != null && !SYS_ADMIN.equals(user.getRole())) {
            user.setRole(SYS_ADMIN);
            userMapper.updateById(user);
            log.warn("已把账号 {} 提升为 SYS_ADMIN（引导配置 app.auth.bootstrap.username）", username);
        }
    }

    private SysUserEntity find(String username) {
        return userMapper.selectOne(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getUsername, username)
                .last("limit 1"));
    }
}
