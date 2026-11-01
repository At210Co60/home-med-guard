package com.homemedguard.common.contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 成员评估上下文：② 规则引擎判禁忌人群、算儿童剂量的唯一入参来源。
 * 只允许携带判定所需的标签码与分级，禁止塞病历原文（快照会落 risk_evaluation_log）。
 *
 * @param tagCodes     禁忌标签码，如 PREGNANT / LACTATING / CHILD / EPILEPSY
 * @param allergyTerms 过敏史**原始写法**（"青霉素类"、"阿司匹林"）。归一化是 ② 的职责：
 *                     ① 不碰成分字典，避免同一个归一化逻辑在两个模块里各写一遍还写歪
 * @param weightKg     最近一次体重，儿童按 mg·kg⁻¹·d⁻¹ 上限判定必需，缺则 ② 标记判定不完整
 * @param liverGrade   Child-Pugh A/B/C，未知为 null
 * @param renalEgfr    eGFR mL·min⁻¹·1.73m⁻²，未知为 null（② 侧按保守策略处理）
 */
public record MemberContextDTO(
        Long memberId,
        Long familyId,
        String displayName,
        LocalDate birthDate,
        BigDecimal weightKg,
        Set<String> tagCodes,
        List<String> allergyTerms,
        String liverGrade,
        Integer renalEgfr) {

    public MemberContextDTO {
        tagCodes = tagCodes == null ? Set.of() : Set.copyOf(tagCodes);
        allergyTerms = allergyTerms == null ? List.of() : List.copyOf(allergyTerms);
    }
}
