package com.homemedguard.service.rule.core;

/**
 * 相互作用严重度。口径与说明书/指南一致，别自创：
 * A 禁止同用或必须医嘱确认（前端 blocking 弹窗）；B 需要调整（间隔/减量/监测）；C 知晓即可。
 */
public enum Severity {

    A("严重", true),
    B("慎用", false),
    C("注意", false);

    private final String label;
    private final boolean blocking;

    Severity(String label, boolean blocking) {
        this.label = label;
        this.blocking = blocking;
    }

    public String label() {
        return label;
    }

    public boolean blocking() {
        return blocking;
    }

    /** 排序用：ordinal 越小越严重。 */
    public static Severity max(Severity left, Severity right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.ordinal() <= right.ordinal() ? left : right;
    }

    public static Severity parse(String raw) {
        for (Severity value : values()) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        return C;
    }
}
