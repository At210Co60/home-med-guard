package com.homemedguard.service.rule.core;

import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.error.BizException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

/**
 * 剂量单位归一到 mg。IU/国际单位必须按成分配置换算系数，没有系数就不许蒙——
 * 维D、胰岛素这类算错比算不出来危险得多。
 */
public final class UnitConverter {

    private static final Map<String, BigDecimal> TO_MG = Map.of(
            "mg", BigDecimal.ONE,
            "g", new BigDecimal("1000"),
            "mcg", new BigDecimal("0.001"),
            "ug", new BigDecimal("0.001"));

    private UnitConverter() {
    }

    public static BigDecimal toMilligrams(BigDecimal amount, String unit) {
        if (amount == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "剂量不能为空");
        }
        String key = unit == null ? "mg" : unit.trim().toLowerCase(Locale.ROOT);
        BigDecimal factor = TO_MG.get(key);
        if (factor == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "单位 " + unit + " 暂不支持自动换算（IU 需按成分录入换算系数），请改用 mg 填报");
        }
        return amount.multiply(factor);
    }
}
