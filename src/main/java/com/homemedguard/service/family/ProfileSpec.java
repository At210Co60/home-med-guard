package com.homemedguard.service.family;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/** 成员档案写入参数。allergyHistory / chronicConditions 是自由文本，落库前加密、归一化交给 ②。 */
public record ProfileSpec(
        String displayName,
        LocalDate birthDate,
        String gender,
        BigDecimal weightKg,
        Set<String> tagCodes,
        String allergyHistory,
        String chronicConditions,
        String liverGrade,
        Integer renalEgfr) {
}
