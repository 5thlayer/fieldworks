// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.github._5thlayer.fieldworks.harvester.OperationChoice.IdleReason;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The words the menu and the Jade tooltip share (#13), built from a {@link HarvesterStatus} and made of translatable
 * components only, so they are the same on both and need no client to build.
 */
public final class HarvesterStatusLines {

    private HarvesterStatusLines() {
    }

    /** The idle reason in words: the choice module's reason, by name. */
    public static Component idle(IdleReason reason) {
        return Component.translatable("fieldworks.harvester.idle." + reason.name().toLowerCase(Locale.ROOT));
    }

    /** "Working", or the idle reason in red. */
    public static Component state(Optional<IdleReason> idle) {
        return idle.map(reason -> idle(reason).copy().withStyle(ChatFormatting.RED))
                .orElseGet(() -> Component.translatable("fieldworks.harvester.working").withStyle(ChatFormatting.GREEN));
    }

    /** The burn left of the last fuel item, in operations. */
    public static Component burn(HarvesterStatus status) {
        return Component.translatable("fieldworks.harvester.burn", String.format(Locale.ROOT, "%.1f", status.burnOperations()));
    }

    public static Component energy(HarvesterStatus status) {
        NumberFormat number = NumberFormat.getIntegerInstance();
        return Component.translatable("fieldworks.harvester.energy", number.format(status.energy()), number.format(status.energyCapacity()));
    }

    /** One of a Harvester's two tanks, as its line names it. */
    public enum Tank {
        DRILLING,
        PRODUCT;

        /** What the tank holds, in {@code status}. */
        public FluidStack stack(HarvesterStatus status) {
            return this == DRILLING ? status.drillingFluidStack() : status.productStack();
        }
    }

    /** A tank's line: the fluid's name and its mB of the capacity. */
    public static Component tank(Tank tank, HarvesterStatus status) {
        NumberFormat number = NumberFormat.getIntegerInstance();
        FluidStack fluid = tank.stack(status);
        return Component.translatable("fieldworks.harvester.tank." + tank.name().toLowerCase(Locale.ROOT),
                fluid.isEmpty() ? Component.translatable("fieldworks.harvester.empty") : fluid.getHoverName(),
                number.format(fluid.getAmount()), number.format(status.tankCapacity()));
    }

    public static Component heldName(Optional<HeldRecipeView> held) {
        return held.map(view -> view.result().isEmpty() ? Component.literal(view.id().toString()) : view.result().getHoverName())
                .orElseGet(() -> Component.translatable("fieldworks.harvester.no_held_recipe").withStyle(ChatFormatting.GRAY));
    }

    /**
     * The tooltip Jade shows, a line each: the state or idle reason, the burn of a tier that takes fuel, the progress
     * and the Held recipe. FE and both tanks are Jade's own, read from the capabilities.
     */
    public static List<Component> lines(HarvesterStatus status, Optional<HeldRecipeView> held) {
        List<Component> lines = new ArrayList<>();
        lines.add(state(status.idle()));
        if (status.takesFuel()) {
            lines.add(burn(status));
        }
        lines.add(Component.translatable("fieldworks.harvester.progress", Math.round(status.progressFraction() * 100)));
        lines.add(Component.translatable("fieldworks.harvester.held", heldName(held)));
        return lines;
    }
}
