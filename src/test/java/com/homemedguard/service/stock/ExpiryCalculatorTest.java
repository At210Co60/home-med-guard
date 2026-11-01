package com.homemedguard.service.stock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 双轨时效表驱动用例：取严不取宽，规则缺失时退回有效期。 */
class ExpiryCalculatorTest {

    private final ExpiryCalculator calculator = new ExpiryCalculator();

    static Stream<Arguments> useByCases() {
        return Stream.of(
                Arguments.of("未开封只看有效期",
                        d(2027, 3, 1), null, 28, d(2027, 3, 1)),
                Arguments.of("开封后轨道更早：滴眼液 2026-03-01 开封，28 天期限压过 2027 有效期",
                        d(2027, 3, 1), d(2026, 3, 1), 28, d(2026, 3, 29)),
                Arguments.of("有效期更早时有效期胜出：糖浆开封 90 天，但 30 天后就到期",
                        d(2026, 4, 1), d(2026, 3, 1), 90, d(2026, 4, 1)),
                Arguments.of("剂型没有开封规则时退回有效期，不编造期限",
                        d(2027, 3, 1), d(2026, 3, 1), null, d(2027, 3, 1)),
                Arguments.of("开封规则天数非法（0）同样退回有效期",
                        d(2027, 3, 1), d(2026, 3, 1), 0, d(2027, 3, 1)),
                Arguments.of("两轨同一天，结果就是那天",
                        d(2026, 3, 29), d(2026, 3, 1), 28, d(2026, 3, 29))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("useByCases")
    @DisplayName("双轨合并取 min")
    void useByDateTakesTheStricterTrack(String label, LocalDate expiry, LocalDate opened,
                                        Integer openDays, LocalDate expected) {
        assertThat(calculator.useByDate(expiry, opened, openDays)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{1} 距 {2} 还差 {0} 天")
    @CsvSource({
            "180, 2026-11-01, 2027-04-30",
            "0,   2026-11-01, 2026-11-01",
            "-1,  2026-11-01, 2026-10-31",
            "29,  2026-11-01, 2026-11-30"
    })
    @DisplayName("剩余天数含边界：到期当天算 0 天，不提前判过期")
    void daysLeftOnTheBoundary(long expected, String from, String deadline) {
        assertThat(calculator.daysLeft(d(from), d(deadline))).isEqualTo(expected);
    }

    @Test
    @DisplayName("期限未知不预警：返回极大值而不是抛异常")
    void unknownDeadlineNeverWarns() {
        assertThat(calculator.daysLeft(LocalDate.of(2026, 11, 1), null)).isEqualTo(Long.MAX_VALUE);
    }

    private static LocalDate d(int y, int m, int day) {
        return LocalDate.of(y, m, day);
    }

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }
}
