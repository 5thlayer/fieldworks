// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester.client;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.fieldworks.harvester.HarvesterMenu;
import io.github._5thlayer.fieldworks.harvester.HarvesterStatus;
import io.github._5thlayer.fieldworks.harvester.HarvesterStatusLines;
import io.github._5thlayer.fieldworks.harvester.HeldRecipeView;
import io.github._5thlayer.fieldworks.network.ClearHeldRecipePacket;
import io.github._5thlayer.libworks.ghost.Ghost;
import io.github._5thlayer.libworks.ghost.Ghosts;
import io.github._5thlayer.libworks.ghost.client.GhostDraw;
import io.github._5thlayer.libworks.machine.client.MachineDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Harvester's screen (#13): a stand-in drawn from fills, like the other machines' until there is art. It draws
 * what {@link HarvesterMenu#status()} holds and {@link HarvesterStatusLines} words, and decides nothing: the power of
 * the kinds the tier declares, both tanks, the output buffer, progress, the Held recipe with a button that clears it,
 * and the idle reason. The button sends {@link ClearHeldRecipePacket}; a recipe is never picked here.
 */
public final class HarvesterScreen extends AbstractContainerScreen<HarvesterMenu> {

    private static final int BURN = 0xFFD9822B;
    private static final int ENERGY = 0xFFB8392B;
    private static final int PROGRESS = 0xFF5DA05D;
    /** The room a bar keeps either side of its label, as {@link MachineDraw#bar} does. */
    private static final int LABEL_PADDING = 4;
    /** The room the Held recipe's line keeps before the Clear button. */
    private static final int BUTTON_GAP = 4;

    private static final int BURN_X = 28;
    private static final int BURN_Y = 20;
    private static final int BURN_WIDTH = 80;
    private static final int POWER_X = 8;
    private static final int POWER_WIDTH = 100;
    private static final int DRILLING_X = 28;
    private static final int DRILLING_Y = 38;
    private static final int PRODUCT_X = 28;
    private static final int PRODUCT_Y = 56;
    private static final int GAUGE_WIDTH = 80;
    private static final int PROGRESS_X = 8;
    private static final int PROGRESS_Y = 74;
    private static final int PROGRESS_WIDTH = 160;
    private static final int HELD_Y = 90;
    /** Where the Held recipe's line starts when its result's icon is drawn before it. */
    private static final int HELD_TEXT_X = 28;
    private static final int CLEAR_X = 134;
    private static final int STATE_Y = 108;

    private Button clear;
    /** What the empty fuel, fill and drain slots show, and what EMI's and JEI's shortcuts ask of. */
    private final Ghosts ghosts = new Ghosts(() -> leftPos, () -> topPos);

    public HarvesterScreen(HarvesterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, HarvesterMenu.INVENTORY_Y + 82);
        inventoryLabelY = HarvesterMenu.INVENTORY_Y - 11;
        for (int index : List.of(HarvesterMenu.FUEL_SLOT, HarvesterMenu.FILL_SLOT, HarvesterMenu.DRAIN_SLOT)) {
            ghosts.add(menu.slots.get(index));
        }
    }

    @Override
    protected void init() {
        super.init();
        clear = addRenderableWidget(Button.builder(Component.translatable("fieldworks.harvester.clear"),
                        button -> ClientPacketDistributor.sendToServer(ClearHeldRecipePacket.INSTANCE))
                .bounds(leftPos + CLEAR_X, topPos + HELD_Y - 2, 34, 18)
                .tooltip(Tooltip.create(Component.translatable("fieldworks.harvester.clear.tooltip")))
                .build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        clear.visible = menu.held().isPresent();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Font font = Minecraft.getInstance().font;
        // The slots' ghosts, over their recesses and under the real stacks and tooltips drawn after.
        MachineDraw.panel(graphics, font, leftPos, topPos, imageWidth, imageHeight, menu.slots, ghosts, Util.getMillis());
        HarvesterStatus status = menu.status();
        if (status.takesFuel()) {
            bar(graphics, font, BURN_X, BURN_Y, burnWidth(status), status.burnFraction(), BURN, HarvesterStatusLines.burn(status));
        }
        if (status.takesEnergy()) {
            bar(graphics, font, energyX(status), BURN_Y, energyWidth(status), charge(status), ENERGY, HarvesterStatusLines.energy(status));
        }
        MachineDraw.gauge(graphics, font, leftPos + DRILLING_X, topPos + DRILLING_Y, GAUGE_WIDTH, status.drillingFluidStack(), status.tankCapacity());
        MachineDraw.gauge(graphics, font, leftPos + PRODUCT_X, topPos + PRODUCT_Y, GAUGE_WIDTH, status.productStack(), status.tankCapacity());
        bar(graphics, font, PROGRESS_X, PROGRESS_Y, PROGRESS_WIDTH, status.progressFraction(), PROGRESS,
                Component.translatable("fieldworks.harvester.progress", Math.round(status.progressFraction() * 100)));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        HarvesterStatus status = menu.status();
        Optional<HeldRecipeView> held = menu.held();
        held.ifPresent(view -> graphics.item(view.result(), MachineDraw.MARGIN, HELD_Y));
        graphics.text(font, MachineDraw.fitted(font, heldLine(held), heldRight(held) - heldX(held)), heldX(held), HELD_Y + 4, MachineDraw.TEXT, false);
        graphics.text(font, MachineDraw.fitted(font, HarvesterStatusLines.state(status.idle()), stateWidth()), MachineDraw.MARGIN, STATE_Y, MachineDraw.TEXT, false);
    }

    /** The ghost stack under the mouse, with the top-left of its slot on screen so EMI's and JEI's recipe and usage keys work on it. */
    public Optional<Ghost> ghostAt(double mouseX, double mouseY) {
        return ghosts.at(mouseX, mouseY, Util.getMillis());
    }

    private static Component heldLine(Optional<HeldRecipeView> held) {
        return Component.translatable("fieldworks.harvester.held", HarvesterStatusLines.heldName(held));
    }

    /** Where the Held recipe's line starts: after its result's icon, when there is one. */
    private static int heldX(Optional<HeldRecipeView> held) {
        return held.map(view -> view.result().isEmpty()).orElse(true) ? MachineDraw.MARGIN : HELD_TEXT_X;
    }

    /** Where the Held recipe's line must end: before the Clear button, while it shows. */
    private int heldRight(Optional<HeldRecipeView> held) {
        return held.isPresent() ? CLEAR_X - BUTTON_GAP : imageWidth - MachineDraw.MARGIN;
    }

    private int stateWidth() {
        return imageWidth - 2 * MachineDraw.MARGIN;
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        HarvesterStatus status = menu.status();
        int x = mouseX - leftPos;
        int y = mouseY - topPos;
        Optional<HeldRecipeView> held = menu.held();
        Optional<Ghost> ghost = ghostAt(mouseX, mouseY);
        if (ghost.isPresent()) {
            GhostDraw.tooltip(graphics, font, ghost.get(), mouseX, mouseY);
        } else if (status.takesFuel() && overBar(BURN_X, BURN_Y, burnWidth(status), x, y)) {
            MachineDraw.wholeOnHover(graphics, font, HarvesterStatusLines.burn(status), burnWidth(status) - LABEL_PADDING, mouseX, mouseY);
        } else if (status.takesEnergy() && overBar(energyX(status), BURN_Y, energyWidth(status), x, y)) {
            MachineDraw.wholeOnHover(graphics, font, HarvesterStatusLines.energy(status), energyWidth(status) - LABEL_PADDING, mouseX, mouseY);
        } else if (overBar(DRILLING_X, DRILLING_Y, GAUGE_WIDTH, x, y)) {
            tankTooltip(graphics, HarvesterStatusLines.Tank.DRILLING, status, mouseX, mouseY);
        } else if (overBar(PRODUCT_X, PRODUCT_Y, GAUGE_WIDTH, x, y)) {
            tankTooltip(graphics, HarvesterStatusLines.Tank.PRODUCT, status, mouseX, mouseY);
        } else if (MachineDraw.over(heldX(held), HELD_Y, heldRight(held) - heldX(held), MachineDraw.SLOT_SIZE, x, y)) {
            MachineDraw.wholeOnHover(graphics, font, heldLine(held), heldRight(held) - heldX(held), mouseX, mouseY);
        } else if (MachineDraw.over(MachineDraw.MARGIN, STATE_Y, stateWidth(), font.lineHeight, x, y)) {
            MachineDraw.wholeOnHover(graphics, font, HarvesterStatusLines.state(status.idle()), stateWidth(), mouseX, mouseY);
        }
    }

    private void tankTooltip(GuiGraphicsExtractor graphics, HarvesterStatusLines.Tank tank, HarvesterStatus status, int mouseX, int mouseY) {
        MachineDraw.fluidTooltip(graphics, font, tank.title(), tank.stack(status), status.tankCapacity(), mouseX, mouseY);
    }

    private static boolean overBar(int x, int y, int width, int mouseX, int mouseY) {
        return MachineDraw.over(x, y, width, MachineDraw.BAR_HEIGHT, mouseX, mouseY);
    }

    // The power bars share the fuel row: an FE-only tier has no fuel slot, and a tier taking both splits the row.
    private static int burnWidth(HarvesterStatus status) {
        return status.takesEnergy() ? (POWER_WIDTH - BURN_X + POWER_X) / 2 - 2 : BURN_WIDTH;
    }

    private static int energyX(HarvesterStatus status) {
        return status.takesFuel() ? BURN_X + burnWidth(status) + 4 : POWER_X;
    }

    private static int energyWidth(HarvesterStatus status) {
        return POWER_X + POWER_WIDTH - energyX(status);
    }

    private static float charge(HarvesterStatus status) {
        return status.energyCapacity() <= 0 ? 0f : Math.min(1f, (float) status.energy() / status.energyCapacity());
    }

    /** A bar at ({@code x}, {@code y}) from the panel's corner. */
    private void bar(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, float fraction, int colour, Component label) {
        MachineDraw.bar(graphics, font, leftPos + x, topPos + y, width, MachineDraw.BAR_HEIGHT, fraction, colour, label);
    }
}
