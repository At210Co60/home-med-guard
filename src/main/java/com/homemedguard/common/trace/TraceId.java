package com.homemedguard.common.trace;

import org.slf4j.MDC;

/** 一次请求/一次评估共享的链路号；风险命中记录靠它把"为什么"串起来。 */
public final class TraceId {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";
    public static final String NONE = "-";

    private TraceId() {
    }

    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null ? NONE : value;
    }
}
