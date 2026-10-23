// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.groundworks.Footprint;
import io.github._5thlayer.groundworks.FootprintPartBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A Harvester's part block. The nine blocks of its bottom layer draw themselves, as casing or, where their column's
 * source lies directly under the Harvester, as the walls round the drill head the renderer draws there; the upper layers
 * are drawn by the origin's model, so draw nothing, as every Groundworks part does.
 */
public final class HarvesterPartBlock extends FootprintPartBlock {

    private final MapCodec<HarvesterPartBlock> codec;

    public HarvesterPartBlock(Properties properties, Supplier<Footprint> footprint) {
        super(properties, footprint);
        this.codec = simpleCodec(props -> new HarvesterPartBlock(props, footprint));
        registerDefaultState(defaultBlockState().setValue(HarvesterBlock.DRILL_HEAD, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HarvesterBlock.DRILL_HEAD);
    }

    /** Whether the part numbered {@code part} stands in the bottom layer. */
    static boolean inBottomLayer(int part) {
        return Harvesters.SHAPE.hasPart(part) && Harvesters.SHAPE.offsetOfPart(part).y() == 0;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return inBottomLayer(state.getValue(PART)) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }
}
