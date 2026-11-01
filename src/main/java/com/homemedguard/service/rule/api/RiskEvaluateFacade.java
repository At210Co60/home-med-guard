package com.homemedguard.service.rule.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * ② 对外暴露的唯一决策接口。①③ 只能这样问，不许注入 ② 的 Mapper。
 * 结果是"已知规则命中提醒"，不是"用药安全检查已通过"——措辞是合规红线。
 */
public interface RiskEvaluateFacade {

    String DISCLAIMER = "本系统为家庭用药提醒辅助工具，规则库不完整，不构成诊疗建议，用药请遵医嘱";

    /**
     * @param drugDictId   走药盒/字典入库的药：此时 doseAmount 是剂型单位数量（如 2 片）
     * @param componentRef 自建药或已知成分：此时按 componentRef 归一化，doseAmount 是质量
     * @param doseUnit     mg / g / mcg，缺省 mg；IU 会直接报参数错
     * @param timesPerDay  每日次数，重复成分叠加按 singleDose × timesPerDay 累加
     */
    record DoseInput(Long drugDictId, String componentRef, BigDecimal doseAmount, String doseUnit, Integer timesPerDay) {
    }

    /**
     * @param scenario REGIMEN_ADD 方案里加药 / PRESCRIBE 新开医嘱 / FAMILY_SCAN 全家药箱体检
     * @param targets  非药品目标，如 ALCOHOL、GRAPEFRUIT_JUICE
     */
    record Request(Long memberId, String scenario, List<DoseInput> items, List<String> targets) {
    }

    record RiskItem(String kind,
                    String ruleCode,
                    Integer ruleVersion,
                    String severity,
                    String severityLabel,
                    boolean blocking,
                    List<String> components,
                    String mechanism,
                    String advice,
                    String basis,
                    Map<String, String> evidence,
                    String sourceRef,
                    String sourceDate) {
    }

    /** 说明书里核对过的应急要点，命中 A 级风险时随报告下发（前端做"立即就医"强提示横幅）。 */
    record EmergencyGuide(String componentCode, String sectionCode, String content, String sourceRef, String sourceDate) {
    }

    /**
     * @param failedRuleCodes    表达式求值失败的规则，前端要显示"本次判定不完整"
     * @param incompleteReasons  缺体重、成分未归一、规则库无该成分上限等客观缺口
     */
    record Report(String traceId,
                  String ruleSetStamp,
                  String maxSeverity,
                  boolean blocking,
                  List<RiskItem> items,
                  List<EmergencyGuide> emergencyGuides,
                  List<String> failedRuleCodes,
                  List<String> incompleteReasons,
                  String disclaimer) {
    }

    Report evaluate(Request request);
}
