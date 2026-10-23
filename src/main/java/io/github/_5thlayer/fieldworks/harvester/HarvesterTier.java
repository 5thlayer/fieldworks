// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A Harvester Tier's figures, read from its file, {@code data/<ns>/harvester_tier/<block>.json}
 * (ADR 0005): its rank, the speed its operations run at, the power it takes, its FE buffer, and its reach,
 * how far down its columns look past the block under it (ADR 0008). A file the codec refuses says why, and its
 * Harvester stays idle.
 */
public record HarvesterTier(int rank, double speed, Power power, int feBuffer, int reach) {

    /**
     * The power a tier takes: burn time from fuel, FE at a cost per tick of work, or both. A fuel's burn time is spent
     * {@code burnPerOperation} to an operation however long it runs, as a furnace spends 200 on a smelt, so a coal
     * buys the same operations of every kind.
     */
    public record Power(boolean fuel, int fePerTick, int burnPerOperation) {

        static final Codec<Power> CODEC = RecordCodecBuilder.<Power>create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("fuel", false).forGetter(Power::fuel),
                Codec.INT.optionalFieldOf("fe_per_tick", 0).forGetter(Power::fePerTick),
                Codec.INT.optionalFieldOf("burn_per_operation", 200).forGetter(Power::burnPerOperation)
        ).apply(instance, Power::new)).validate(power -> power.burnPerOperation() >= 1 ? DataResult.success(power)
                : DataResult.error(() -> "a tier's burn_per_operation must be at least 1, not " + power.burnPerOperation()));

        /**
         * The burn time the tick at {@code progress} of an operation {@code ticks} long spends: the operation's
         * {@link #burnPerOperation} spread over its ticks, so its ticks together spend exactly that.
         */
        public int burnShare(int progress, int ticks) {
            long perOperation = burnPerOperation;
            return (int) (perOperation * (progress + 1) / ticks - perOperation * progress / ticks);
        }

        /** The operations {@code burnTime} buys. */
        public float operations(int burnTime) {
            return (float) burnTime / burnPerOperation;
        }

        public boolean fe() {
            return fePerTick > 0;
        }

        /**
         * What pays for the next tick of work, given the burn time left of the fuel item last lit and the FE
         * buffered (#7): a started burn finishes first, then FE pays while a tick's cost is buffered, and only
         * then is fuel lit. A burn outlives a reload that took the tier's fuel away.
         */
        public Payment payment(int burnLeft, int feStored) {
            if (burnLeft > 0) {
                return Payment.BURN;
            }
            if (fe() && feStored >= fePerTick) {
                return Payment.FE;
            }
            return fuel ? Payment.LIGHT : Payment.NONE;
        }
    }

    /** What pays for a tick of work: burn time, a tick's FE, lighting a fuel item to burn, or nothing. */
    public enum Payment {
        BURN, FE, LIGHT, NONE
    }

    public static final Codec<HarvesterTier> CODEC = RecordCodecBuilder.<HarvesterTier>create(instance -> instance.group(
            Codec.INT.fieldOf("rank").forGetter(HarvesterTier::rank),
            Codec.DOUBLE.fieldOf("speed").forGetter(HarvesterTier::speed),
            Power.CODEC.fieldOf("power").forGetter(HarvesterTier::power),
            Codec.INT.optionalFieldOf("fe_buffer", 0).forGetter(HarvesterTier::feBuffer),
            Codec.INT.optionalFieldOf("reach", 0).forGetter(HarvesterTier::reach)
    ).apply(instance, HarvesterTier::new)).validate(HarvesterTier::check);

    private static DataResult<HarvesterTier> check(HarvesterTier tier) {
        if (!(tier.speed > 0)) {
            return DataResult.error(() -> "a tier's speed must be above 0, not " + tier.speed);
        }
        if (tier.reach < 0) {
            return DataResult.error(() -> "a tier's reach cannot be negative, not " + tier.reach);
        }
        if (tier.power.fePerTick < 0 || tier.feBuffer < 0) {
            return DataResult.error(() -> "a tier's FE figures cannot be negative");
        }
        if (!tier.power.fuel && !tier.power.fe()) {
            return DataResult.error(() -> "a tier that takes no power could never work: give it fuel, fe_per_tick or both");
        }
        if (tier.power.fe() && tier.feBuffer < tier.power.fePerTick) {
            return DataResult.error(() -> "a tier's fe_buffer of " + tier.feBuffer
                    + " cannot hold one tick's cost of " + tier.power.fePerTick);
        }
        return DataResult.success(tier);
    }

    /** How long an operation of {@code duration} ticks at speed ×1 takes on this tier: at least a tick. */
    public int ticks(int duration) {
        return Math.max(1, (int) Math.round(duration / speed));
    }
}
