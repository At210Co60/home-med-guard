package com.homemedguard.service.rule.web;

import com.homemedguard.common.api.Result;
import com.homemedguard.common.audit.AuditLog;
import com.homemedguard.service.rule.RuleModule;
import com.homemedguard.service.rule.entity.DrugLeafletEntity;
import com.homemedguard.service.rule.governance.ComponentLeafletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "06-说明书要点")
@RuleModule
@RestController
@RequestMapping("/api/leaflets")
public class LeafletController {

    public record LeafletForm(String componentCode, String sectionCode, String content,
                              String sourceRef, LocalDate sourceDate) {
    }

    private final ComponentLeafletService leafletService;

    public LeafletController(ComponentLeafletService leafletService) {
        this.leafletService = leafletService;
    }

    @Operation(summary = "录入/更新某成分某一章节的说明书要点（仅系统管理员，必须带来源与核对日期）")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @AuditLog(action = "LEAFLET_UPSERT", targetType = "drug_leaflet")
    @PostMapping
    public Result<DrugLeafletEntity> upsert(@RequestBody LeafletForm form) {
        return Result.ok(leafletService.upsert(form.componentCode(), form.sectionCode(), form.content(),
                form.sourceRef(), form.sourceDate()));
    }

    @Operation(summary = "查看某成分已核对的要点（风险页展开用）")
    @GetMapping("/{componentCode}")
    public Result<List<ComponentLeafletService.LeafletView>> list(@PathVariable String componentCode) {
        return Result.ok(leafletService.listByComponent(componentCode));
    }
}
