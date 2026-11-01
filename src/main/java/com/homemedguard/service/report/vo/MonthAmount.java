package com.homemedguard.service.report.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 按业务月聚合的金额行，ym 形如 202611。 */
@Getter
@Setter
public class MonthAmount {

    private Integer ym;

    private BigDecimal amount;
}
