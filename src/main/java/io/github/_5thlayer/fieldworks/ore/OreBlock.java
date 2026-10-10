// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * An ore block of a patch: an amount, drawn as one of eight stages. The block holds no amount; it
 * derives from the patch and the difference is a chunk attachment ({@link OreDelta}), because a
 * patch is thousands of blocks and a block entity apiece is not affordable. The blockstate carries
 * only the stage, recomputed from the amount.
 */
public final class OreBlock extends Block {

    public static final IntegerProperty STAGE =
            IntegerProperty.create("stage", 0, OreCorpus.get().stageCount() - 1);

    private final OreResource resource;

    public OreBlock(OreResource resource, BlockBehaviour.Properties props) {
        super(props
                .mapColor(MapColor.STONE)
                .requiresCorrectToolForDrops()
                .noLootTable()
                .strength(resource.hardness(), Math.max(resource.hardness(), 3.0f))
                // A pushed block would arrive full, its delta retired behind it.
                .pushReaction(PushReaction.BLOCK)
                .sound(SoundType.STONE));
        this.resource = resource;
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
    }

    public OreResource resource() {
        return resource;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    public BlockState stateFor(int remaining, int initial) {
        return defaultBlockState()
                .setValue(STAGE, OreStage.stage(remaining, initial, resource.corpus().stageRatios()));
    }

    /** Every removal goes through here, so a delta never outlives its block. A stage change is not a removal. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
                                               boolean movedByPiston) {
        OreMining.onRemoved(level, pos, this);
    }
}
