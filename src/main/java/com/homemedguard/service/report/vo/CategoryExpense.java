package com.homemedguard.service.report.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 支出趋势的一格：某月某类别。 */
@Getter
@Setter
public class CategoryExpense {

    private Integer ym;

    /** 按剂型归类（家庭在册药的快照列）。 */
    private String category;

    private BigDecimal amount;

    private Integer batches;
}
