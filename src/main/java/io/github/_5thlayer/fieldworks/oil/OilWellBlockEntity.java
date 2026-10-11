// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.oil;

import io.github._5thlayer.fieldworks.FieldworksRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A well's amount, and the amount it started with, which its depletion floor is read against (ADR-0002). */
public class OilWellBlockEntity extends BlockEntity {

    private static final WellYield YIELD = WellYield.fromCorpus();

    private long amount;
    private long initial;
    private long carry;

    public OilWellBlockEntity(BlockPos pos, BlockState state) {
        super(FieldworksRegistries.OIL_WELL_ENTITY.get(), pos, state);
    }

    public void start(long amount) {
        this.amount = amount;
        this.initial = amount;
        this.carry = 0;
        setChanged();
    }

    public long amount() {
        return amount;
    }

    public long initial() {
        return initial;
    }

    /** Whether the well has decayed to its floor, where its yield stays (ADR-0002). */
    public boolean atFloor() {
        return amount <= YIELD.floor(initial);
    }

    public double yield() {
        return YIELD.yield(amount);
    }

    /** The crude the next cycle would yield, without running it. */
    public int nextCycle() {
        return YIELD.cycle(amount, initial, carry).crude();
    }

    /** The crude of a cycle at {@code fraction} of the yield the well had at its starting amount. */
    public int restored(double fraction) {
        return YIELD.restored(initial, fraction);
    }

    /** Runs one cycle: the crude it yields, the well depleted by it. */
    public int cycle() {
        WellYield.Cycle cycle = YIELD.cycle(amount, initial, carry);
        amount = cycle.amount();
        carry = cycle.carry();
        setChanged();
        return cycle.crude();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        amount = input.getLongOr("Amount", 0L);
        initial = input.getLongOr("Initial", amount);
        carry = input.getLongOr("Carry", 0L);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("Amount", amount);
        output.putLong("Initial", initial);
        output.putLong("Carry", carry);
    }
}
