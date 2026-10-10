// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.marker;

import java.util.Locale;

/** A patch's amount in short form, or an oil field's yield, floored so a label never claims more than is there. */
public final class PatchAmount {

    private PatchAmount() {
    }

    public static String format(long amount) {
        if (amount < 1_000) {
            return Long.toString(amount);
        }
        if (amount < 1_000_000) {
            return shorten(amount, 1_000, "k");
        }
        return shorten(amount, 1_000_000, "M");
    }

    /** An oil field's summed yield, floored to a whole percent of {@code normal}. */
    public static String yield(long amount, long normal) {
        return String.format(Locale.ROOT, "%,d%%", amount * 100 / normal);
    }

    private static String shorten(long amount, long unit, String suffix) {
        long whole = amount / unit;
        if (whole >= 100) {
            return whole + suffix;
        }
        long tenths = amount * 10 / unit % 10;
        return whole + "." + tenths + suffix;
    }
}
