package com.homemedguard.service.stock;

import com.homemedguard.service.stock.FefoAllocator.Allocation;
import com.homemedguard.service.stock.FefoAllocator.Batch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** FEFO 表驱动用例：先到期先出、过期批次不参与、不足时部分分配。 */
class FefoAllocatorTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 11, 1);

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("单批次够扣，整笔落在最早到期的一批",
                        List.of(new Batch(1L, d(2027, 3, 1), 10), new Batch(2L, d(2026, 12, 1), 10)),
                        6, "2:6"),
                Arguments.of("跨批次：先吃 12 月到期的，再补 2027-03 的",
                        List.of(new Batch(1L, d(2027, 3, 1), 10), new Batch(2L, d(2026, 12, 1), 4)),
                        6, "2:4,1:2"),
                Arguments.of("已过期批次不能被消耗掉，剩下 0 可分",
                        List.of(new Batch(1L, d(2026, 10, 1), 5)),
                        3, ""),
                Arguments.of("刚好用完一批，不留数量为 0 的分配",
                        List.of(new Batch(1L, d(2026, 12, 1), 5), new Batch(2L, d(2027, 1, 1), 5)),
                        5, "1:5"),
                Arguments.of("数量为 0 的批次直接跳过",
                        List.of(new Batch(1L, d(2026, 12, 1), 0), new Batch(2L, d(2027, 1, 1), 3)),
                        2, "2:2"),
                Arguments.of("期限为空的批次排在最后，不抢在前面出",
                        List.of(new Batch(1L, null, 9), new Batch(2L, d(2027, 5, 1), 2)),
                        3, "2:2,1:1"),
                Arguments.of("库存不足只给部分，由调用方比对总量",
                        List.of(new Batch(1L, d(2026, 12, 1), 2), new Batch(2L, d(2027, 1, 1), 2)),
                        9, "1:2,2:2"),
                Arguments.of("需求为 0 不产生分配",
                        List.of(new Batch(1L, d(2026, 12, 1), 5)),
                        0, "")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("FEFO 分配")
    void plansByEarliestUseByDate(String label, List<Batch> batches, int required, String expected) {
        List<Allocation> plan = FefoAllocator.plan(batches, required, AS_OF);
        assertThat(join(plan)).isEqualTo(expected);
        assertThat(FefoAllocator.totalAllocated(plan)).isLessThanOrEqualTo(required);
        assertThat(plan).allMatch(a -> a.quantity() > 0);
    }

    @Test
    @DisplayName("期限边界：今天到期的还能出，昨天到期的不再出")
    void boundaryIsInclusiveOnUseByDate() {
        List<Allocation> today = FefoAllocator.plan(
                List.of(new Batch(1L, LocalDate.of(2026, 11, 1), 5)), 3, AS_OF);
        assertThat(join(today)).isEqualTo("1:3");

        List<Allocation> yesterday = FefoAllocator.plan(
                List.of(new Batch(1L, LocalDate.of(2026, 10, 31), 5)), 3, AS_OF);
        assertThat(yesterday).isEmpty();
    }

    /** 期望串写成 "批次id:数量,批次id:数量"，顺序即消费顺序。 */
    private static String join(List<Allocation> plan) {
        StringBuilder sb = new StringBuilder();
        for (Allocation a : plan) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(a.batchId()).append(':').append(a.quantity());
        }
        return sb.toString();
    }

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }
}
