// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A Harvester's origin block: the footprint's block that holds the block entity and the facing, which
 * is the output face. Standing at the bottom centre, it is one of the nine bottom blocks that can be
 * drawn as a drill head. Its other 26 positions are Groundworks' part blocks.
 */
public final class HarvesterBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** Whether this bottom-layer block is its column's drill head, its source lying directly under the Harvester. */
    public static final BooleanProperty DRILL_HEAD = BooleanProperty.create("drill_head");

    /** The block light a lit Harvester gives off, as a lit furnace does. */
    public static final int LIT_LIGHT = 13;

    /** Whether the Harvester is working: an operation progressed on its last tick. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final TierBlock tier;
    private final MapCodec<HarvesterBlock> codec;

    public HarvesterBlock(Properties properties, TierBlock tier) {
        super(properties.lightLevel(state -> state.getValue(LIT) ? LIT_LIGHT : 0));
        this.tier = tier;
        this.codec = simpleCodec(props -> new HarvesterBlock(props, tier));
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(DRILL_HEAD, false).setValue(LIT, false));
    }

    public TierBlock tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DRILL_HEAD, LIT);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
        return HarvesterPartBlock.hidesFace(state, neighborState, direction);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HarvesterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel) || type != Harvesters.BLOCK_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, entity) -> ((HarvesterBlockEntity) entity).serverTick((ServerLevel) world);
    }

    /** Opens the Harvester's menu; Groundworks' parts forward a click on any of them here. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HarvesterBlockEntity harvester) {
            player.openMenu(harvester, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** Going takes the footprint's parts with it. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Harvesters.footprint(tier).teardown(level, pos, state.getValue(FACING), pos);
    }
}
