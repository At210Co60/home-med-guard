package com.homemedguard.common.contract;

import java.time.LocalDate;
import java.util.Set;

/**
 * 成员评估上下文：② 规则引擎判禁忌人群、算儿童剂量的唯一入参来源。
 * 只允许携带判定所需的标签码与分级，禁止塞病历原文（快照会落 risk_evaluation_log）。
 *
 * @param tagCodes             禁忌标签码，如 PREGNANT / LACTATING / CHILD / EPILEPSY
 * @param allergyComponentCodes 过敏史已归一化到标准成分码；未归一化的原始文本留在 ① 侧
 * @param liverGrade            Child-Pugh A/B/C，未知为 null
 * @param renalEgfr             eGFR mL·min⁻¹·1.73m⁻²，未知为 null（② 侧按保守策略处理）
 */
public record MemberContextDTO(
        Long memberId,
        Long familyId,
        String displayName,
        LocalDate birthDate,
        Set<String> tagCodes,
        Set<String> allergyComponentCodes,
        String liverGrade,
        Integer renalEgfr) {

    public MemberContextDTO {
        tagCodes = tagCodes == null ? Set.of() : Set.copyOf(tagCodes);
        allergyComponentCodes = allergyComponentCodes == null ? Set.of() : Set.copyOf(allergyComponentCodes);
    }
}
