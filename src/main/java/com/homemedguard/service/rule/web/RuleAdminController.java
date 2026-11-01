package com.homemedguard.service.rule.web;

import com.homemedguard.common.api.Result;
import com.homemedguard.common.audit.AuditLog;
import com.homemedguard.service.auth.core.SessionUser;
import com.homemedguard.service.rule.RuleModule;
import com.homemedguard.service.rule.entity.ComponentDictEntity;
import com.homemedguard.service.rule.entity.RulePublishLogEntity;
import com.homemedguard.service.rule.governance.ComponentAdminService;
import com.homemedguard.service.rule.governance.GovernedRule;
import com.homemedguard.service.rule.governance.RuleDraftForm;
import com.homemedguard.service.rule.governance.RuleGovernanceService;
import com.homemedguard.service.rule.governance.RuleStatus;
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

import java.time.LocalDate;
import java.util.List;

/**
 * 后台规则治理。只有系统管理员能改规则，家庭管理员不行——
 * 规则库是全局数据，写错会影响所有家庭，必须集中审核。
 */
@Tag(name = "05-规则审核发布")
@RuleModule
@RestController
@RequestMapping("/api/admin/rules")
@PreAuthorize("hasRole('SYS_ADMIN')")
public class RuleAdminController {

    public record ReasonForm(String reason, LocalDate effectiveFrom) {
    }

    public record AliasForm(String alias) {
    }

    private final RuleGovernanceService governance;
    private final ComponentAdminService componentAdmin;

    public RuleAdminController(RuleGovernanceService governance, ComponentAdminService componentAdmin) {
        this.governance = governance;
        this.componentAdmin = componentAdmin;
    }

    @Operation(summary = "新建标准成分（同时把标准名登记为别名，归一化词表就在这两张表）")
    @AuditLog(action = "COMPONENT_CREATE", targetType = "component_dict")
    @PostMapping("/components")
    public Result<ComponentDictEntity> createComponent(@RequestBody ComponentAdminService.NewComponent form) {
        return Result.ok(componentAdmin.createComponent(form));
    }

    @Operation(summary = "给成分加别名，如 扑热息痛 / acetaminophen / APAP")
    @AuditLog(action = "COMPONENT_ALIAS_ADD", targetType = "component_alias")
    @PostMapping("/components/{componentId}/aliases")
    public Result<Void> addAlias(@PathVariable Long componentId, @RequestBody AliasForm form) {
        componentAdmin.addAlias(componentId, form.alias());
        return Result.ok();
    }

    @Operation(summary = "新建或修改规则草稿（已发布版本不可改，只能开新版本）")
    @AuditLog(action = "RULE_DRAFT_UPSERT", targetType = "rule")
    @PostMapping("/{table}")
    public Result<GovernedRule> upsertDraft(@PathVariable String table, @RequestBody RuleDraftForm form) {
        return Result.ok(governance.upsertDraft(table, form, SessionUser.requireId()));
    }

    @Operation(summary = "提交审核 DRAFT → PENDING")
    @AuditLog(action = "RULE_SUBMIT", targetType = "rule")
    @PostMapping("/{table}/{ruleCode}/{version}/submit")
    public Result<GovernedRule> submit(@PathVariable String table, @PathVariable String ruleCode,
                                       @PathVariable int version) {
        return Result.ok(governance.submit(table, ruleCode, version, SessionUser.requireId()));
    }

    @Operation(summary = "审核通过并发布 PENDING → PUBLISHED（缺来源或表达式编译不过会被拒绝）")
    @AuditLog(action = "RULE_APPROVE", targetType = "rule")
    @PostMapping("/{table}/{ruleCode}/{version}/approve")
    public Result<GovernedRule> approve(@PathVariable String table, @PathVariable String ruleCode,
                                        @PathVariable int version,
                                        @RequestBody(required = false) ReasonForm form) {
        return Result.ok(governance.approve(table, ruleCode, version, SessionUser.requireId(),
                form == null ? null : form.effectiveFrom()));
    }

    @Operation(summary = "驳回 PENDING → DRAFT，必须写理由")
    @AuditLog(action = "RULE_REJECT", targetType = "rule")
    @PostMapping("/{table}/{ruleCode}/{version}/reject")
    public Result<GovernedRule> reject(@PathVariable String table, @PathVariable String ruleCode,
                                       @PathVariable int version, @RequestBody ReasonForm form) {
        return Result.ok(governance.reject(table, ruleCode, version, SessionUser.requireId(), form.reason()));
    }

    @Operation(summary = "停用 PUBLISHED → RETIRED，必须写理由")
    @AuditLog(action = "RULE_RETIRE", targetType = "rule")
    @PostMapping("/{table}/{ruleCode}/{version}/retire")
    public Result<GovernedRule> retire(@PathVariable String table, @PathVariable String ruleCode,
                                       @PathVariable int version, @RequestBody ReasonForm form) {
        return Result.ok(governance.retire(table, ruleCode, version, SessionUser.requireId(), form.reason()));
    }

    @Operation(summary = "按状态列出规则")
    @GetMapping("/{table}")
    public Result<List<GovernedRule>> list(@PathVariable String table,
                                           @RequestParam(defaultValue = RuleStatus.DRAFT) String status) {
        return Result.ok(governance.listByStatus(table, status));
    }

    @Operation(summary = "某条规则的流转历史（谁在什么时候动了什么）")
    @GetMapping("/{table}/{ruleCode}/history")
    public Result<List<RulePublishLogEntity>> history(@PathVariable String table, @PathVariable String ruleCode) {
        return Result.ok(governance.history(table, ruleCode));
    }
}
