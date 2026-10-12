// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.text.NumberFormat;
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

    /** One of a Harvester's two tanks, as its tooltip and Jade name it. */
    public enum Tank {
        DRILLING,
        PRODUCT;

        /** What the tank holds, in {@code status}. */
        public FluidStack stack(HarvesterStatus status) {
            return this == DRILLING ? status.drillingFluidStack() : status.productStack();
        }

        /** The tank's name, such as "Drilling Fluid". */
        public Component title() {
            return Component.translatable("fieldworks.harvester.tank_title." + name().toLowerCase(Locale.ROOT));
        }
    }

    public static Component heldName(Optional<HeldRecipeView> held) {
        return held.map(view -> view.result().isEmpty() ? Component.literal(view.id().toString()) : view.result().getHoverName())
                .orElseGet(() -> Component.translatable("fieldworks.harvester.no_held_recipe").withStyle(ChatFormatting.GRAY));
    }
}
