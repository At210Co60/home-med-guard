package com.homemedguard.web;

import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.api.Result;
import com.homemedguard.common.contract.DrugBasicDTO;
import com.homemedguard.common.error.BizException;
import com.homemedguard.service.auth.core.LoginUser;
import com.homemedguard.service.auth.core.SessionUser;
import com.homemedguard.service.drug.DrugDictService;
import com.homemedguard.service.drug.DrugModule;
import com.homemedguard.service.drug.DrugSpec;
import com.homemedguard.service.drug.entity.DrugDictEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "04-药品字典")
@DrugModule
@RestController
@RequestMapping("/api/drugs")
public class DrugController {

    private static final String BARCODE_MISS = "条码未收录，请手工确认并入库";

    public record BarcodeForm(String barcode) {
    }

    public record BindForm(String barcode, Long drugDictId) {
    }

    private final DrugDictService drugDictService;

    public DrugController(DrugDictService drugDictService) {
        this.drugDictService = drugDictService;
    }

    @Operation(summary = "名称自动补全，返回 top10")
    @GetMapping("/suggest")
    public Result<List<DrugBasicDTO>> suggest(@RequestParam("kw") String keyword) {
        LoginUser user = SessionUser.require();
        return Result.ok(drugDictService.suggest(keyword, user.userId(), isSysAdmin(user)));
    }

    @Operation(summary = "药品详情")
    @GetMapping("/{id}")
    public Result<DrugBasicDTO> detail(@PathVariable Long id) {
        LoginUser user = SessionUser.require();
        return Result.ok(DrugDictService.toDto(drugDictService.requireVisible(id, user.userId(), isSysAdmin(user))));
    }

    @Operation(summary = "录入药品：系统管理员进字典，普通用户进自建药，随后由 ② 做成分归一化")
    @PostMapping
    public Result<DrugBasicDTO> create(@RequestBody DrugSpec spec) {
        LoginUser user = SessionUser.require();
        return Result.ok(DrugDictService.toDto(drugDictService.create(user.userId(), spec, isSysAdmin(user))));
    }

    @Operation(summary = "条码/追溯码识别：查不到返回 404，前端据此走手工录入")
    @PostMapping("/barcode/resolve")
    public Result<DrugBasicDTO> resolveBarcode(@RequestBody BarcodeForm form) {
        LoginUser user = SessionUser.require();
        DrugDictEntity drug = drugDictService.resolveBarcode(form.barcode())
                .filter(found -> found.visibleTo(user.userId(), isSysAdmin(user)))
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, BARCODE_MISS));
        return Result.ok(DrugDictService.toDto(drug));
    }

    @Operation(summary = "绑定条码到字典项（仅系统管理员）")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @PostMapping("/barcode/bind")
    public Result<Void> bindBarcode(@RequestBody BindForm form) {
        drugDictService.bindBarcode(form.barcode(), form.drugDictId());
        return Result.ok();
    }

    private static boolean isSysAdmin(LoginUser user) {
        return "SYS_ADMIN".equals(user.role());
    }
}
