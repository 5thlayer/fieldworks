// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/** A Harvester's state. */
public final class HarvesterBlockEntity extends BlockEntity {

    public HarvesterBlockEntity(BlockPos pos, BlockState state) {
        super(Harvesters.BLOCK_ENTITY.get(), pos, state);
    }

    void serverTick(ServerLevel level) {
    }

    @Nullable ResourceHandler<ItemResource> itemFace() {
        return null;
    }
}
