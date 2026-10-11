// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * How a Harvester pays for its work (#7): its fuel slot, the burn of the fuel item last lit and its FE buffer. One burn
 * tick, or one tick's FE, buys one tick of work.
 */
final class HarvesterPower {

    private static final String FUEL_KEY = "fuel";
    private static final String ENERGY_KEY = "energy";
    private static final String BURN_KEY = "burn_left";
    private static final String BURN_TOTAL_KEY = "burn_total";

    private final ItemStacksResourceHandler fuel;
    /** Where a lit fuel item's remainder goes: the output buffer. */
    private final ItemStacksResourceHandler remainders;
    private final EnergyBuffer energy;

    /** Burn ticks bought by the last fuel item and not yet worked. */
    private int burnLeft;
    /** The burn the last fuel item bought, so the menu can draw {@link #burnLeft} against it (#13). */
    private int burnTotal;

    HarvesterPower(ItemStacksResourceHandler remainders, Runnable changed) {
        this.fuel = new HarvesterSlots(1, changed);
        this.remainders = remainders;
        this.energy = new EnergyBuffer(changed);
    }

    ItemStacksResourceHandler fuel() {
        return fuel;
    }

    /**
     * The energy capability, sized by the tier's FE buffer: it takes FE and gives none back. A tier that takes no FE
     * exposes none (ADR 0005), and neither does one whose file is missing.
     */
    @Nullable EnergyHandler energyFace(@Nullable HarvesterTier tier) {
        if (tier == null || !tier.power().fe()) {
            return null;
        }
        sizeEnergy(tier);
        return energy.face;
    }

    int burnLeft() {
        return burnLeft;
    }

    int burnTotal() {
        return burnTotal;
    }

    /** The FE buffered, in a buffer sized to {@code tier}'s. */
    int energy(@Nullable HarvesterTier tier) {
        sizeEnergy(tier);
        return energy.getAmountAsInt();
    }

    /** Sizes the FE buffer to the tier's, on a tier that takes FE: a reload can change it. */
    private void sizeEnergy(@Nullable HarvesterTier tier) {
        if (tier != null && tier.power().fe()) {
            energy.resize(tier.feBuffer());
        }
    }

    /**
     * Pays for one tick of work as the tier's power rule says, when {@code commit}, or says whether it could: a burn
     * tick, a tick's FE, or a fuel item lit.
     */
    boolean pay(ServerLevel level, HarvesterTier tier, boolean commit) {
        sizeEnergy(tier);
        return switch (tier.power().payment(burnLeft, energy.getAmountAsInt())) {
            case BURN -> {
                if (commit) {
                    burnLeft--;
                }
                yield true;
            }
            case FE -> {
                if (commit) {
                    try (Transaction paying = Transaction.openRoot()) {
                        energy.extract(tier.power().fePerTick(), paying);
                        paying.commit();
                    }
                }
                yield true;
            }
            case LIGHT -> {
                if (!light(level, commit)) {
                    yield false;
                }
                if (commit) {
                    burnLeft--;
                }
                yield true;
            }
            case NONE -> false;
        };
    }

    /**
     * Lights the fuel item in the fuel slot, when {@code commit}, or says whether it would light. A fuel item's
     * remainder, the bucket of a lava bucket, goes to the output buffer, so a fuel item whose remainder has no room
     * there is not lit.
     */
    private boolean light(ServerLevel level, boolean commit) {
        ItemResource item = fuel.getResource(0);
        int burnTime = item.isEmpty() ? 0 : item.toStack(1).getBurnTime(null, level.fuelValues());
        if (burnTime == 0) {
            return false;
        }
        try (Transaction lighting = Transaction.openRoot()) {
            fuel.extract(0, item, 1, lighting);
            ItemStackTemplate remainder = item.toStack(1).getCraftingRemainder();
            if (remainder != null && ResourceHandlerUtil.insertStacking(remainders,
                    ItemResource.of(remainder.create()), 1, lighting) != 1) {
                return false;
            }
            if (!commit) {
                return true;
            }
            lighting.commit();
        }
        burnLeft = burnTime;
        burnTotal = burnTime;
        return true;
    }

    void save(ValueOutput output) {
        fuel.serialize(output.child(FUEL_KEY));
        energy.serialize(output.child(ENERGY_KEY));
        output.putInt(BURN_KEY, burnLeft);
        output.putInt(BURN_TOTAL_KEY, burnTotal);
    }

    void load(ValueInput input) {
        fuel.deserialize(input.childOrEmpty(FUEL_KEY));
        energy.deserialize(input.childOrEmpty(ENERGY_KEY));
        burnLeft = input.getIntOr(BURN_KEY, 0);
        burnTotal = input.getIntOr(BURN_TOTAL_KEY, 0);
    }

    /**
     * The FE buffer, sized by the tier on each use since a reload can change it. It is drawn only by the
     * Harvester's own work; {@link #face} takes FE in and gives none out.
     */
    private static final class EnergyBuffer extends SimpleEnergyHandler {

        private final Runnable changed;

        final EnergyHandler face = new EnergyHandler() {
            @Override
            public long getAmountAsLong() {
                return EnergyBuffer.this.getAmountAsLong();
            }

            @Override
            public long getCapacityAsLong() {
                return EnergyBuffer.this.getCapacityAsLong();
            }

            @Override
            public int insert(int amount, TransactionContext transaction) {
                return EnergyBuffer.this.insert(amount, transaction);
            }

            @Override
            public int extract(int amount, TransactionContext transaction) {
                return 0;
            }
        };

        EnergyBuffer(Runnable changed) {
            super(0);
            this.changed = changed;
        }

        void resize(int capacity) {
            this.capacity = capacity;
            this.maxInsert = capacity;
            this.maxExtract = capacity;
            this.energy = Math.min(energy, capacity);
        }

        @Override
        protected void onEnergyChanged(int previousAmount) {
            changed.run();
        }
    }
}
