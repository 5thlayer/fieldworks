// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.groundworks.Footprint;
import io.github._5thlayer.groundworks.FootprintPartBlock;
import net.minecraft.core.Direction;
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

    // Per instance, as the origin's is: each tier's part block names its own footprint.
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

    /**
     * Whether {@code state} is a Harvester's bottom block drawn as casing: the origin or a bottom-layer part, no drill
     * head. Such a block draws a face toward each neighbour inside the footprint, which shows only toward a drill head.
     */
    static boolean isCasingFoot(BlockState state) {
        if (state.getBlock() instanceof HarvesterBlock) {
            return !state.getValue(HarvesterBlock.DRILL_HEAD);
        }
        return state.getBlock() instanceof HarvesterPartBlock
                && inBottomLayer(state.getValue(PART)) && !state.getValue(HarvesterBlock.DRILL_HEAD);
    }

    /** Whether a casing bottom block's side face toward {@code neighbor} is hidden: the neighbour is casing too. */
    static boolean hidesFace(BlockState state, BlockState neighbor, Direction direction) {
        return direction.getAxis().isHorizontal() && isCasingFoot(state) && isCasingFoot(neighbor);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
        return hidesFace(state, neighborState, direction);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return inBottomLayer(state.getValue(PART)) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }
}
