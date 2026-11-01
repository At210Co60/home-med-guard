package com.homemedguard.service.stock;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * FEFO（先到期先出）分配：纯函数，不碰数据库，便于表驱动测试。
 * useByDate 为空的批次排在最后（期限未知，不抢在前面出）。
 */
public final class FefoAllocator {

    public record Batch(Long batchId, LocalDate useByDate, int quantity) {
    }

    public record Allocation(Long batchId, LocalDate useByDate, int quantity) {
    }

    private FefoAllocator() {
    }

    /**
     * @param asOf 出库业务日；已过期限的批次直接跳过，过期药不能被消耗掉再提醒处置
     * @return 按效期升序的分配方案；库存不足时返回能分到的部分，由调用方比对总量
     */
    public static List<Allocation> plan(List<Batch> batches, int required, LocalDate asOf) {
        List<Allocation> result = new ArrayList<>();
        if (required <= 0 || batches == null || batches.isEmpty()) {
            return result;
        }
        List<Batch> usable = batches.stream()
                .filter(b -> b.batchId() != null && b.quantity() > 0)
                .filter(b -> b.useByDate() == null || !b.useByDate().isBefore(asOf))
                .sorted(Comparator.comparing(Batch::useByDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Batch::batchId))
                .toList();

        int remaining = required;
        for (Batch batch : usable) {
            if (remaining <= 0) {
                break;
            }
            int take = Math.min(remaining, batch.quantity());
            result.add(new Allocation(batch.batchId(), batch.useByDate(), take));
            remaining -= take;
        }
        return result;
    }

    public static int totalAllocated(List<Allocation> allocations) {
        return allocations.stream().mapToInt(Allocation::quantity).sum();
    }
}
