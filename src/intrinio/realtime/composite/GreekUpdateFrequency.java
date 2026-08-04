package intrinio.realtime.composite;

import java.util.EnumSet;

/**
 * Bit-flag style enumeration controlling when {@link GreekClient} recalculates Greeks.
 * <p>
 * Combine values with {@link EnumSet} (or {@link #combine(EnumSet)}) and pass them to
 * {@link GreekClient}. Multiple flags may be set so that Greeks recompute on several event types.
 * </p>
 */
public enum GreekUpdateFrequency {
    /** Recalculate on every options trade that updates the cache. */
    EVERY_OPTIONS_TRADE_UPDATE(1),
    /** Recalculate on every options quote that updates the cache. */
    EVERY_OPTIONS_QUOTE_UPDATE(2),
    /** Recalculate all contracts when the top-level risk-free rate supplemental datum changes. */
    EVERY_RISK_FREE_INTEREST_RATE_UPDATE(4),
    /** Recalculate a security's contracts when its dividend-yield supplemental datum changes. */
    EVERY_DIVIDEND_YIELD_UPDATE(8),
    /** Recalculate a security's contracts on every equities trade update. */
    EVERY_EQUITY_TRADE_UPDATE(16),
    /** Recalculate a security's contracts on every equities quote update. */
    EVERY_EQUITY_QUOTE_UPDATE(32);

    /** Power-of-two flag value (mirrors the C# {@code [Flags]} enum). */
    private final int value;

    GreekUpdateFrequency(int value) {
        this.value = value;
    }

    /**
     * @return integer flag bit for this frequency
     */
    public int getValue() {
        return value;
    }

    /**
     * Combines an enum set into a single bitmask integer.
     *
     * @param set frequencies to combine
     * @return bitwise OR of all flag values
     */
    public static int combine(EnumSet<GreekUpdateFrequency> set) {
        int combined = 0;
        if (set == null) {
            return 0;
        }
        for (GreekUpdateFrequency freq : set) {
            combined |= freq.getValue();
        }
        return combined;
    }

    /**
     * Reconstructs an {@link EnumSet} from a bitmask integer.
     *
     * @param value bitmask
     * @return set of matching frequencies
     */
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
