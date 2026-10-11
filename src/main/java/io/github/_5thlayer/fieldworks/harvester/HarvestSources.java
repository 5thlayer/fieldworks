// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.Set;
import java.util.stream.Collectors;

import io.github._5thlayer.fieldworks.FieldworksTags;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Kind;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.Source;
import io.github._5thlayer.fieldworks.oil.OilWellBlock;
import io.github._5thlayer.fieldworks.oil.OilWellBlockEntity;
import io.github._5thlayer.fieldworks.ore.OreBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a block is to a Harvester: an oil well it pumps, an ore block it draws, a harvestable block it breaks, or none.
 * The layer it works, the work it does and the Default recipes a viewer lists all read it here.
 */
final class HarvestSources {

    private HarvestSources() {
    }

    static Kind kindOf(BlockState state) {
        Block block = state.getBlock();
        return block instanceof OilWellBlock ? Kind.WELL
                : block instanceof OreBlock ? Kind.ORE_BLOCK
                // After the ore blocks: they are in c:ores, so in this tag, and are drawn, never broken (#1).
                : state.is(FieldworksTags.HARVESTABLE) ? Kind.HARVESTABLE
                : Kind.NONE;
    }

    /** The source the block at {@code pos} is, with a well's floor state. */
    static Source at(Level level, BlockPos pos) {
        return of(level.getBlockState(pos), level.getBlockEntity(pos) instanceof OilWellBlockEntity well && well.atFloor());
    }

    /** The source a block in {@code state} is, {@code atFloor} being a well's floor state. */
    static Source of(BlockState state, boolean atFloor) {
        Block block = state.getBlock();
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        return switch (kindOf(state)) {
            case WELL -> atFloor ? Source.flooredWell(id) : Source.well(id);
            case ORE_BLOCK -> Source.oreBlock(id, tagsOf(block));
            case HARVESTABLE -> Source.harvestable(id, tagsOf(block));
            case NONE -> Source.NONE;
        };
    }

    private static Set<String> tagsOf(Block block) {
        return block.builtInRegistryHolder().tags().map(tag -> tag.location().toString()).collect(Collectors.toUnmodifiableSet());
    }
}
