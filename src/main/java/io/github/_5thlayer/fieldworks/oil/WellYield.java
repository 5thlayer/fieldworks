// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.oil;

/**
 * What a well yields each cycle and what the cycle takes off it (ADR-0002). Crude is whole
 * millibuckets, so a cycle's fraction is carried to the next rather than floored away.
 */
public record WellYield(long normal, long minimum, long depletion, int amountPerCycle) {

    /** The most crude one cycle yields, however rich the well. */
    public static final int MAX_PER_CYCLE = 1_000;

    /** A well never decays below this share of its starting amount, if that is above {@code minimum}. */
    private static final int FLOOR_PERCENT_OF_INITIAL = 20;

    /**
     * @param crude millibuckets this cycle
     * @param amount the well's amount after it
     * @param carry the fraction owed to the next cycle, in units of {@code 1/normal} mB
     */
    public record Cycle(int crude, long amount, long carry) {
    }

    public static WellYield fromCorpus() {
        OilCorpus corpus = OilCorpus.get();
        return new WellYield(corpus.normal(), corpus.minimum(), corpus.depletion(), corpus.amountPerCycle());
    }

    public double yield(long amount) {
        return (double) amount / normal;
    }

    public long floor(long initial) {
        return Math.max(minimum, initial * FLOOR_PERCENT_OF_INITIAL / 100);
    }

    /**
     * The crude of a cycle at {@code fraction} of the yield the well had at its starting amount, {@code initial}, in
     * whole mB with the remainder dropped, never more than {@link #MAX_PER_CYCLE}.
     */
    public int restored(long initial, double fraction) {
        // The epsilon keeps a product such as 0.7 x 10 from flooring to one under the whole it stands for.
        long crude = (long) Math.floor(fraction * amountPerCycle * initial / normal + 1e-9);
        return (int) Math.min(crude, MAX_PER_CYCLE);
    }

    public Cycle cycle(long amount, long initial, long carry) {
        long owed = amountPerCycle * amount + carry;
        long crude = owed / normal;
        long left = owed % normal;
        if (crude >= MAX_PER_CYCLE) {
            crude = MAX_PER_CYCLE;
            left = 0;
        }
        long floor = floor(initial);
        long after = amount <= floor ? amount : Math.max(floor, amount - depletion);
        return new Cycle((int) crude, after, left);
    }
}
