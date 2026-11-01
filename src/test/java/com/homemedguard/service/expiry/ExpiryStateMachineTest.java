package com.homemedguard.service.expiry;

import com.homemedguard.service.expiry.ExpiryStateMachine.Thresholds;
import com.homemedguard.service.expiry.ExpiryStateMachine.Verdict;
import com.homemedguard.service.stock.api.StockQueryFacade.BatchView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 三级预警表驱动用例：档位边界、开封超期与已过效期的区分、期限未知不预警。 */
class ExpiryStateMachineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
    private static final Thresholds DEFAULTS = new Thresholds(180, 90, 30);

    private final ExpiryStateMachine machine = new ExpiryStateMachine();

    static Stream<Arguments> cases() {
        return Stream.of(
                // 距最终日, 期望批次状态, 期望任务类型, 期望档位
                Arguments.of(200, "NORMAL", null, null),
                Arguments.of(181, "NORMAL", null, null),
                Arguments.of(180, "EXPIRING_NEAR", "EXPIRY_NEAR", "LOW"),
                Arguments.of(91, "EXPIRING_NEAR", "EXPIRY_NEAR", "LOW"),
                Arguments.of(90, "EXPIRING_NEAR", "EXPIRY_NEAR", "MID"),
                Arguments.of(31, "EXPIRING_NEAR", "EXPIRY_NEAR", "MID"),
                Arguments.of(30, "EXPIRING_NEAR", "EXPIRY_NEAR", "HIGH"),
                Arguments.of(1, "EXPIRING_NEAR", "EXPIRY_NEAR", "HIGH"),
                Arguments.of(0, "EXPIRING_NEAR", "EXPIRY_NEAR", "HIGH"),
                Arguments.of(-1, "EXPIRED", "EXPIRED", "HIGH"),
                Arguments.of(-400, "EXPIRED", "EXPIRED", "HIGH")
        );
    }

    @ParameterizedTest(name = "距最终日 {0} 天 -> {1} / {2} / {3}")
    @MethodSource("cases")
    void classifiesByDaysLeft(int daysLeft, String status, String taskType, String level) {
        LocalDate useBy = TODAY.plusDays(daysLeft);
        // 有效期与最终日同日：说明压线的是有效期那一轨
        Verdict verdict = machine.evaluate(TODAY, batch(useBy, useBy), DEFAULTS);

        assertThat(verdict.batchStatus()).isEqualTo(status);
        assertThat(verdict.taskType()).isEqualTo(taskType);
        assertThat(verdict.level()).isEqualTo(level);
    }

    @Test
    @DisplayName("开封后使用期先压线时，任务类型是 OPENED_OVERDUE 而不是 EXPIRED")
    void distinguishesTheOpeningTrack() {
        LocalDate expiry = LocalDate.of(2027, 6, 1);
        LocalDate useBy = LocalDate.of(2026, 10, 20);

        Verdict verdict = machine.evaluate(TODAY, batch(expiry, useBy), DEFAULTS);

        assertThat(verdict.batchStatus()).isEqualTo("EXPIRED");
        assertThat(verdict.taskType()).isEqualTo("OPENED_OVERDUE");
    }

    @Test
    @DisplayName("期限未知不预警，也不把批次踢出巡检")
    void unknownDeadlineProducesNoTask() {
        Verdict verdict = machine.evaluate(TODAY, batch(null, null), DEFAULTS);

        assertThat(verdict.taskType()).isNull();
        assertThat(verdict.batchStatus()).isEqualTo("NORMAL");
        assertThat(verdict.nextCheckDate()).isNull();
    }

    @Test
    @DisplayName("三档被填成乱序时重排，不是一天连响三次")
    void scrambedThresholdsAreReordered() {
        Thresholds messy = new Thresholds(30, 180, 90);

        assertThat(messy.normalized()).isEqualTo(new Thresholds(180, 90, 30));
        assertThat(messy.normalized().urgentDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("下次巡检日推进到更紧的那一档")
    void nextCheckWalksIntoTheTighterTier() {
        LocalDate useBy = TODAY.plusDays(120);

        Verdict low = machine.evaluate(TODAY, batch(useBy, useBy), DEFAULTS);
        assertThat(low.nextCheckDate()).isEqualTo(useBy.minusDays(90));

        Verdict mid = machine.evaluate(useBy.minusDays(90), batch(useBy, useBy), DEFAULTS);
        assertThat(mid.nextCheckDate()).isEqualTo(useBy.minusDays(30));

        Verdict high = machine.evaluate(useBy.minusDays(30), batch(useBy, useBy), DEFAULTS);
        assertThat(high.nextCheckDate()).isEqualTo(useBy);

        Verdict expired = machine.evaluate(useBy.plusDays(1), batch(useBy, useBy), DEFAULTS);
        assertThat(expired.nextCheckDate()).isNull();
    }

    private static BatchView batch(LocalDate expiryDate, LocalDate useByDate) {
        return new BatchView(1L, 1L, 1L, "B001", expiryDate, null, useByDate, 10, "PCS", "NORMAL", "阿司匹林肠溶片");
    }
}
