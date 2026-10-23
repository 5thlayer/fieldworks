// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.List;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A drill head a Harvester places in the cell above a source that lies below the block directly under it
 * (GLOSSARY.md, Drill head): never placed by hand and with no item. It takes a replaceable block's or a fluid's
 * place as Beltworks' wedge does, waterlogs in a water source and leaves the water when it goes, and destroys any
 * other fluid it takes the place of. It collides with nothing but keeps a selection outline, holds its cell, and
 * breaking it dismantles the Harvester. It draws nothing: the Harvester's renderer draws it.
 */
public final class DrillHeadBlock extends Block implements SimpleWaterloggedBlock {

    private static final MapCodec<DrillHeadBlock> CODEC = simpleCodec(DrillHeadBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

    /** Whether this thread is taking a Harvester's own heads away, so their going dismantles nothing. */
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);

    public DrillHeadBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.WATERLOGGED);
    }

    /** The head laid in the cell at {@code pos}: waterlogged over a water source, whatever else was there lost. */
    static BlockState placedAt(BlockGetter level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        boolean water = fluid.is(Fluids.WATER) && fluid.isSource();
        return Harvesters.DRILL_HEAD.get().defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, water);
    }

    /** Takes away the heads at these positions, the Harvester's own doing: it leaves water where a head waterlogged. */
    static void removeAll(Level level, List<BlockPos> heads) {
        boolean was = REMOVING.get();
        REMOVING.set(true);
        try {
            for (BlockPos pos : heads) {
                if (level.getBlockState(pos).getBlock() instanceof DrillHeadBlock) {
                    // removeBlock leaves a waterlogged cell's water; setBlock to air would not.
                    level.removeBlock(pos, false);
                }
            }
        } finally {
            REMOVING.set(was);
        }
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** A player's break dismantles the Harvester with their tool, as breaking one of its parts does. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos owner = HarvesterBlockEntity.showing(level, pos);
            if (owner != null) {
                level.destroyBlock(owner, !player.preventsBlockDrops(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Going any other way, by an explosion or a command, dismantles the Harvester with no tool. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (!REMOVING.get()) {
            BlockPos owner = HarvesterBlockEntity.showing(level, pos);
            if (owner != null) {
                level.destroyBlock(owner, true);
            }
        }
    }
}
