package intrinio.realtime.composite;

import java.util.EnumSet;

public enum GreekUpdateFrequency {
    EVERY_OPTIONS_TRADE_UPDATE(1),
    EVERY_OPTIONS_QUOTE_UPDATE(2),
    EVERY_RISK_FREE_INTEREST_RATE_UPDATE(4),
    EVERY_DIVIDEND_YIELD_UPDATE(8),
    EVERY_EQUITY_TRADE_UPDATE(16),
    EVERY_EQUITY_QUOTE_UPDATE(32);

    private final int value;

    GreekUpdateFrequency(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static int combine(EnumSet<GreekUpdateFrequency> set) {
        int combined = 0;
        for (GreekUpdateFrequency freq : set) {
            combined |= freq.getValue();
        }
        return combined;
    }

    public static EnumSet<GreekUpdateFrequency> fromValue(int value) {
        EnumSet<GreekUpdateFrequency> set = EnumSet.noneOf(GreekUpdateFrequency.class);
        for (GreekUpdateFrequency freq : values()) {
            if ((value & freq.getValue()) != 0) {
                set.add(freq);
            }
        }
        return set;
    }
}