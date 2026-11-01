package com.homemedguard.web;

import com.homemedguard.common.api.Result;
import com.homemedguard.common.audit.AuditLog;
import com.homemedguard.service.auth.core.SessionUser;
import com.homemedguard.service.family.DataErasureService;
import com.homemedguard.service.family.FamilyModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "01-账号")
@FamilyModule
@RestController
@RequestMapping("/api/me")
public class MeDataController {

    public record EraseForm(String password) {
    }

    private final DataErasureService dataErasureService;

    public MeDataController(DataErasureService dataErasureService) {
        this.dataErasureService = dataErasureService;
    }

    @Operation(summary = "删除我的全部数据（需再次输入密码；跨模块数据由各自模块物理清除）")
    @AuditLog(action = "ACCOUNT_ERASE", targetType = "sys_user")
    @DeleteMapping("/data")
    public Result<DataErasureService.ErasureReport> erase(@RequestBody EraseForm form) {
        return Result.ok(dataErasureService.eraseEverything(SessionUser.requireId(), form.password()));
    }
}
