package com.homemedguard.service.rule.web;

import com.homemedguard.common.api.Result;
import com.homemedguard.service.rule.RuleModule;
import com.homemedguard.service.rule.api.RiskEvaluateFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 风险评估入口。返回 200 + blocking=true 而不是报错：
 * 是否拦住"保存方案"由调用方（③）用 RISK_CONFIRM_REQUIRED 决定，②只负责判。
 */
@Tag(name = "03-用药冲突监护")
@RuleModule
@RestController
@RequestMapping("/api/risk")
public class RiskController {

    public record EvaluateRequest(Long memberId,
                                  String scenario,
                                  List<Dose> items,
                                  List<String> targets) {

        public record Dose(Long drugDictId, String componentRef, java.math.BigDecimal doseAmount,
                           String doseUnit, Integer timesPerDay) {
        }
    }

    private final RiskEvaluateFacade riskEvaluateFacade;

    public RiskController(RiskEvaluateFacade riskEvaluateFacade) {
        this.riskEvaluateFacade = riskEvaluateFacade;
    }

    @Operation(summary = "实时风险评估：成员 + 在服 + 拟新增 → 命中的已知规则")
    @PostMapping("/evaluate")
    public Result<RiskEvaluateFacade.Report> evaluate(@RequestBody EvaluateRequest request) {
        List<RiskEvaluateFacade.DoseInput> items = request.items() == null ? List.of()
                : request.items().stream()
                .map(dose -> new RiskEvaluateFacade.DoseInput(dose.drugDictId(), dose.componentRef(),
                        dose.doseAmount(), dose.doseUnit(), dose.timesPerDay()))
                .toList();
        RiskEvaluateFacade.Report report = riskEvaluateFacade.evaluate(
                new RiskEvaluateFacade.Request(request.memberId(), request.scenario(), items, request.targets()));
        return Result.ok(report);
    }
}
