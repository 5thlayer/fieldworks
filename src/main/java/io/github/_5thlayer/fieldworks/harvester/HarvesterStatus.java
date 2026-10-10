// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.Optional;

import io.github._5thlayer.fieldworks.harvester.OperationChoice.IdleReason;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * What a Harvester shows of itself (#13), read off the block entity in one place: the menu syncs these figures to
 * its screen and the Jade tooltip sends the record whole, so the two cannot disagree. The idle reason is the choice
 * module's own, as the last tick found it.
 *
 * <p>Power is shown only for the kinds the tier declares: a kind it does not take reads {@code false} and zero. A
 * tier with no file reads neither.
 *
 * @param burnLeft      burn ticks left of the fuel item last lit
 * @param burnTotal     the burn that item bought, so the two make a bar
 * @param energy        FE buffered, and {@code energyCapacity} what the buffer holds
 * @param progress      ticks into the operation under way, of {@code operationTicks}
 * @param tankCapacity  the one fixed capacity of both tanks, in mB
 */
public record HarvesterStatus(boolean takesFuel, boolean takesEnergy, int burnLeft, int burnTotal, int energy,
        int energyCapacity, int progress, int operationTicks, Optional<IdleReason> idle, Fluid drillingFluid,
        int drillingAmount, Fluid product, int productAmount, int tankCapacity) {

    private static final StreamCodec<ByteBuf, Optional<IdleReason>> IDLE =
            ByteBufCodecs.optional(ByteBufCodecs.idMapper(ordinal -> IdleReason.values()[ordinal], IdleReason::ordinal));
    private static final StreamCodec<RegistryFriendlyByteBuf, Fluid> FLUID = ByteBufCodecs.registry(Registries.FLUID);

    public static final StreamCodec<RegistryFriendlyByteBuf, HarvesterStatus> STREAM_CODEC = StreamCodec.of(
            (buffer, status) -> {
                buffer.writeBoolean(status.takesFuel);
                buffer.writeBoolean(status.takesEnergy);
                buffer.writeVarInt(status.burnLeft);
                buffer.writeVarInt(status.burnTotal);
                buffer.writeVarInt(status.energy);
                buffer.writeVarInt(status.energyCapacity);
                buffer.writeVarInt(status.progress);
                buffer.writeVarInt(status.operationTicks);
                IDLE.encode(buffer, status.idle);
                FLUID.encode(buffer, status.drillingFluid);
                buffer.writeVarInt(status.drillingAmount);
                FLUID.encode(buffer, status.product);
                buffer.writeVarInt(status.productAmount);
                buffer.writeVarInt(status.tankCapacity);
            },
            buffer -> new HarvesterStatus(buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    IDLE.decode(buffer),
                    FLUID.decode(buffer), buffer.readVarInt(),
                    FLUID.decode(buffer), buffer.readVarInt(),
                    buffer.readVarInt()));

    /** The Drilling Fluid tank's contents, empty when it holds none. */
    public FluidStack drillingFluidStack() {
        return drillingAmount <= 0 ? FluidStack.EMPTY : new FluidStack(drillingFluid, drillingAmount);
    }

    /** The product tank's contents, empty when it holds none. */
    public FluidStack productStack() {
        return productAmount <= 0 ? FluidStack.EMPTY : new FluidStack(product, productAmount);
    }

    /** How far the operation under way is, 0 to 1; 0 before the first. */
    public float progressFraction() {
        return operationTicks <= 0 ? 0f : Math.min(1f, (float) progress / operationTicks);
    }

    /** How much of the last fuel item's burn is left, 0 to 1. */
    public float burnFraction() {
        return burnTotal <= 0 ? 0f : Math.min(1f, (float) burnLeft / burnTotal);
    }
}
