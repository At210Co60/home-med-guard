package com.homemedguard.mapper.report;

import com.homemedguard.service.report.vo.CategoryExpense;
import com.homemedguard.service.report.vo.MonthAmount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 报表 SQL（handoff §8：报表一律走 *ReportMapper 手写 SQL，不用 ORM 拼复杂查询）。
 * 只读 ② 自己的 stock_batch / disposal_record / family_drug，不 JOIN 别人的表。
 * 语句在 resources/mapper/report/StockReportMapper.xml。
 */
@Mapper
public interface StockReportMapper {

    /** 购药总额，按 purchase_date 所在月聚合——浪费率的分母。 */
    List<MonthAmount> selectPurchasedMonthly(@Param("familyId") Long familyId,
                                             @Param("startYm") Integer startYm,
                                             @Param("endYm") Integer endYm);

    /** 浪费金额，按处置时间所在月聚合，只算 DISPOSED / RETURNED。 */
    List<MonthAmount> selectWasteMonthly(@Param("familyId") Long familyId,
                                         @Param("startYm") Integer startYm,
                                         @Param("endYm") Integer endYm);

    /** 支出趋势：月 × 剂型。 */
    List<CategoryExpense> selectExpenseByCategory(@Param("familyId") Long familyId,
                                                  @Param("startYm") Integer startYm,
                                                  @Param("endYm") Integer endYm);
}
