// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester.client;

import java.text.NumberFormat;
import java.util.List;
import java.util.Optional;

import io.github._5thlayer.fieldworks.harvester.HarvesterMenu;
import io.github._5thlayer.fieldworks.harvester.HarvesterStatus;
import io.github._5thlayer.fieldworks.harvester.HarvesterStatusLines;
import io.github._5thlayer.fieldworks.harvester.HeldRecipeView;
import io.github._5thlayer.fieldworks.network.ClearHeldRecipePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The Harvester's screen (#13): a stand-in drawn from fills, like the other machines' until there is art. It draws
 * what {@link HarvesterMenu#status()} holds and {@link HarvesterStatusLines} words, and decides nothing: the power of
 * the kinds the tier declares, both tanks, the output buffer, progress, the Held recipe with a button that clears it,
 * and the idle reason. The button sends {@link ClearHeldRecipePacket}; a recipe is never picked here.
 */
public final class HarvesterScreen extends AbstractContainerScreen<HarvesterMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;
    private static final int BURN = 0xFFD9822B;
    private static final int ENERGY = 0xFFB8392B;
    private static final int PROGRESS = 0xFF5DA05D;
    private static final int TEXT = 0xFF404040;
    private static final int WHITE = 0xFFFFFFFF;

    private static final int BAR_HEIGHT = 12;
    private static final int FLUID_TILE = 16;

    private static final int BURN_X = 28;
    private static final int BURN_Y = 20;
    private static final int BURN_WIDTH = 80;
    private static final int ENERGY_X = 8;
    private static final int ENERGY_Y = 36;
    private static final int ENERGY_WIDTH = 100;
    private static final int DRILLING_X = 28;
    private static final int DRILLING_Y = 54;
    private static final int PRODUCT_X = 28;
    private static final int PRODUCT_Y = 74;
    private static final int GAUGE_WIDTH = 80;
    private static final int PROGRESS_X = 8;
    private static final int PROGRESS_Y = 92;
    private static final int PROGRESS_WIDTH = 160;
    private static final int HELD_Y = 108;
    private static final int CLEAR_X = 134;
    private static final FormattedText ELLIPSIS = FormattedText.of("…");
    private static final int STATE_Y = 126;

    private Button clear;

    public HarvesterScreen(HarvesterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, HarvesterMenu.INVENTORY_Y + 82);
        inventoryLabelY = HarvesterMenu.INVENTORY_Y - 11;
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
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                recess(graphics, leftPos + slot.x, topPos + slot.y, 16, 16);
            }
        }
        HarvesterStatus status = menu.status();
        Font font = Minecraft.getInstance().font;
        if (status.takesFuel()) {
            bar(graphics, font, BURN_X, BURN_Y, BURN_WIDTH, status.burnFraction(), BURN, HarvesterStatusLines.burn(status));
        }
        if (status.takesEnergy()) {
            float charge = status.energyCapacity() <= 0 ? 0f : Math.min(1f, (float) status.energy() / status.energyCapacity());
            bar(graphics, font, ENERGY_X, ENERGY_Y, ENERGY_WIDTH, charge, ENERGY, HarvesterStatusLines.energy(status));
        }
        gauge(graphics, DRILLING_X, DRILLING_Y, status.drillingFluidStack(), status.tankCapacity());
        gauge(graphics, PRODUCT_X, PRODUCT_Y, status.productStack(), status.tankCapacity());
        bar(graphics, font, PROGRESS_X, PROGRESS_Y, PROGRESS_WIDTH, status.progressFraction(), PROGRESS,
                Component.translatable("fieldworks.harvester.progress", Math.round(status.progressFraction() * 100)));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        HarvesterStatus status = menu.status();
        Optional<HeldRecipeView> held = menu.held();
        held.ifPresent(view -> graphics.item(view.result(), 8, HELD_Y));
        int x = held.map(view -> view.result().isEmpty()).orElse(true) ? 8 : 28;
        int right = held.isPresent() ? CLEAR_X - 4 : imageWidth - 8;
        graphics.text(font, fitted(heldLine(held), right - x), x, HELD_Y + 4, TEXT, false);
        graphics.text(font, fitted(HarvesterStatusLines.state(status.idle()), imageWidth - 16), 8, STATE_Y, TEXT, false);
    }

    private static Component heldLine(Optional<HeldRecipeView> held) {
        return Component.translatable("fieldworks.harvester.held", HarvesterStatusLines.heldName(held));
    }

    /** {@code line} cut to {@code width} with an ellipsis; hovering a cut line shows it whole. */
    private FormattedCharSequence fitted(Component line, int width) {
        if (font.width(line) <= width) {
            return line.getVisualOrderText();
        }
        FormattedText cut = font.substrByWidth(line, width - font.width(ELLIPSIS));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, ELLIPSIS));
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        HarvesterStatus status = menu.status();
        int x = mouseX - leftPos;
        int y = mouseY - topPos;
        if (over(DRILLING_X, DRILLING_Y, GAUGE_WIDTH, x, y)) {
            tankTooltip(graphics, HarvesterStatusLines.Tank.DRILLING, status, mouseX, mouseY);
        } else if (over(PRODUCT_X, PRODUCT_Y, GAUGE_WIDTH, x, y)) {
            tankTooltip(graphics, HarvesterStatusLines.Tank.PRODUCT, status, mouseX, mouseY);
        } else if (y >= HELD_Y && y < HELD_Y + 16 && x >= 8 && x < CLEAR_X - 4) {
            wholeLine(graphics, heldLine(menu.held()), CLEAR_X - 4 - 28, mouseX, mouseY);
        } else if (y >= STATE_Y && y < STATE_Y + 9 && x >= 8 && x < imageWidth - 8) {
            wholeLine(graphics, HarvesterStatusLines.state(status.idle()), imageWidth - 16, mouseX, mouseY);
        }
    }

    private void wholeLine(GuiGraphicsExtractor graphics, Component line, int width, int mouseX, int mouseY) {
        if (font.width(line) > width) {
            graphics.setComponentTooltipForNextFrame(font, List.of(line), mouseX, mouseY);
        }
    }

    private void tankTooltip(GuiGraphicsExtractor graphics, HarvesterStatusLines.Tank tank, HarvesterStatus status, int mouseX, int mouseY) {
        graphics.setComponentTooltipForNextFrame(font, List.of(HarvesterStatusLines.tank(tank, status)), mouseX, mouseY);
    }

    private static boolean over(int x, int y, int width, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BAR_HEIGHT;
    }

    /** A slot's bevel: dark above and left, light below and right, grey inside. */
    private static void recess(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x - 1, y - 1, x + width, y, SLOT_DARK);
        graphics.fill(x - 1, y, x, y + height, SLOT_DARK);
        graphics.fill(x, y + height, x + width + 1, y + height + 1, SLOT_LIGHT);
        graphics.fill(x + width, y, x + width + 1, y + height, SLOT_LIGHT);
        graphics.fill(x, y, x + width, y + height, SLOT);
    }

    /** A bar in its recess, {@code fraction} of it filled, with {@code label} written over it. */
    private void bar(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, float fraction, int colour, Component label) {
        int left = leftPos + x;
        int top = topPos + y;
        recess(graphics, left, top, width, BAR_HEIGHT);
        graphics.fill(left, top, left + Math.round(width * fraction), top + BAR_HEIGHT, colour);
        graphics.text(font, label, left + (width - font.width(label)) / 2, top + 2, WHITE, true);
    }

    /** A fluid gauge in its recess: the fluid's still texture, tinted as in the world, tiled over the part the tank fills. */
    private void gauge(GuiGraphicsExtractor graphics, int x, int y, FluidStack fluid, int capacity) {
        int left = leftPos + x;
        int top = topPos + y;
        recess(graphics, left, top, GAUGE_WIDTH, BAR_HEIGHT);
        if (fluid.isEmpty()) {
            return;
        }
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
        int tint = model.fluidTintSource() == null ? WHITE : 0xFF000000 | model.fluidTintSource().color(fluid.getFluid().defaultFluidState());
        int filled = Math.min(GAUGE_WIDTH, Math.round(GAUGE_WIDTH * (float) fluid.getAmount() / Math.max(1, capacity)));
        graphics.enableScissor(left, top, left + filled, top + BAR_HEIGHT);
        for (int tileX = left; tileX < left + filled; tileX += FLUID_TILE) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, model.stillMaterial().sprite(), tileX, top, FLUID_TILE, FLUID_TILE, tint);
        }
        graphics.disableScissor();
        Component amount = Component.literal(NumberFormat.getIntegerInstance().format(fluid.getAmount()) + " mB");
        graphics.text(font, amount, left + 2, top + 2, WHITE, true);
    }
}
