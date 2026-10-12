package com.leadorbit.billing.gst;

/** Invoice-level rounding policy applied once after totals are summed. */
public enum RoundingPolicy {
    /** No rounding; totals keep paise. */
    NONE,
    /** Round the grand total to the nearest rupee (Indian invoicing convention). */
    NEAREST_RUPEE
}